/*
 * Copyright (c) 2010-2026 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.fullykiosk.internal;

import static org.openhab.binding.fullykiosk.internal.FullyKioskBindingConstants.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.measure.Quantity;
import javax.measure.Unit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.http.HttpStatus;
import org.openhab.binding.fullykiosk.internal.dto.DeviceInfo;
import org.openhab.binding.fullykiosk.internal.dto.Response;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.library.unit.SIUnits;
import org.openhab.core.library.unit.Units;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;
import org.openhab.core.types.State;
import org.openhab.core.types.UnDefType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

/**
 * The {@link FullyKioskHandler} polls and controls a Fully Kiosk Browser device through its Remote Admin REST
 * interface.
 *
 * @author Keith Knauss - Initial contribution
 */
@NonNullByDefault
public class FullyKioskHandler extends BaseThingHandler {

    private static final long REQUEST_TIMEOUT_SECONDS = 10;
    private static final Gson GSON = new Gson();

    /** Android reports this MAC address when the app is not allowed to read the real one. */
    private static final String PLACEHOLDER_MAC = "02:00:00:00:00:00";

    private final Logger logger = LoggerFactory.getLogger(FullyKioskHandler.class);
    private final HttpClient httpClient;

    private volatile FullyKioskConfiguration config = new FullyKioskConfiguration();
    private volatile @Nullable DeviceInfo lastDeviceInfo;
    private @Nullable ScheduledFuture<?> pollingJob;
    private volatile Map<String, String> lastProperties = Map.of();
    /** Cleared on dispose so that polls and commands still in flight do not publish stale state. */
    private volatile boolean active;

    public FullyKioskHandler(Thing thing, HttpClient httpClient) {
        super(thing);
        this.httpClient = httpClient;
    }

    @Override
    public void initialize() {
        FullyKioskConfiguration config = getConfigAs(FullyKioskConfiguration.class);
        if (config.hostname.isBlank()) {
            updateStatus(ThingStatusDetail.CONFIGURATION_ERROR, "@text/offline.configuration-error.hostname");
            return;
        }
        if (config.password.isBlank()) {
            updateStatus(ThingStatusDetail.CONFIGURATION_ERROR, "@text/offline.configuration-error.password");
            return;
        }
        this.config = config;
        lastProperties = Map.of();

        updateStatus(ThingStatus.UNKNOWN);
        active = true;
        pollingJob = scheduler.scheduleWithFixedDelay(this::poll, 0, config.refreshInterval, TimeUnit.SECONDS);
    }

    @Override
    public void dispose() {
        active = false;
        ScheduledFuture<?> job = pollingJob;
        if (job != null) {
            job.cancel(true);
            pollingJob = null;
        }
        lastDeviceInfo = null;
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (command instanceof RefreshType) {
            DeviceInfo deviceInfo = lastDeviceInfo;
            if (deviceInfo != null) {
                updateChannels(deviceInfo);
            }
            return;
        }

        if (!(command instanceof OnOffType onOff)) {
            logger.debug("Unsupported command {} for channel {}", command, channelUID.getId());
            return;
        }
        boolean on = onOff == OnOffType.ON;
        String fullyCommand;
        switch (channelUID.getId()) {
            case CHANNEL_SCREEN -> fullyCommand = on ? "screenOn" : "screenOff";
            case CHANNEL_SCREENSAVER -> fullyCommand = on ? "startScreensaver" : "stopScreensaver";
            default -> {
                logger.debug("Channel {} does not accept commands", channelUID.getId());
                return;
            }
        }
        scheduler.execute(() -> executeCommand(fullyCommand));
    }

    private void executeCommand(String fullyCommand) {
        if (!active) {
            return;
        }
        try {
            sendCommand(config, fullyCommand, Map.of(), Response.class);
        } catch (FullyKioskDeviceErrorException e) {
            logger.warn("Fully Kiosk rejected command '{}': {}", fullyCommand, e.getDeviceMessage());
            return;
        } catch (FullyKioskCommunicationException e) {
            updateStatus(ThingStatusDetail.COMMUNICATION_ERROR, e.getMessage());
            return;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        // Refresh right away so channels reflect the command instead of waiting for the next poll.
        poll();
    }

    private void poll() {
        if (!active) {
            return;
        }
        try {
            DeviceInfo deviceInfo = sendCommand(config, "deviceInfo", Map.of(), DeviceInfo.class);
            lastDeviceInfo = deviceInfo;
            updateStatus(ThingStatus.ONLINE);
            updateChannels(deviceInfo);

            Map<String, String> properties = buildProperties(deviceInfo);
            if (!properties.equals(lastProperties)) {
                updateProperties(properties);
                lastProperties = properties;
            }
        } catch (FullyKioskCommunicationException e) {
            updateStatus(ThingStatusDetail.COMMUNICATION_ERROR, e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void updateChannels(DeviceInfo deviceInfo) {
        updateState(CHANNEL_SCREEN, toOnOff(deviceInfo.screenOn));
        updateState(CHANNEL_SCREENSAVER, toOnOff(deviceInfo.isInScreensaver));
        updateState(CHANNEL_BATTERY_LEVEL, toDecimal(deviceInfo.batteryLevel));
        updateState(CHANNEL_BATTERY_TEMPERATURE, toQuantity(deviceInfo.batteryTemperature, SIUnits.CELSIUS));
        updateState(CHANNEL_PLUGGED, toOnOff(deviceInfo.plugged));
        updateState(CHANNEL_CURRENT_PAGE, toString(deviceInfo.currentPage));
        updateState(CHANNEL_FOREGROUND_APP, toString(deviceInfo.foregroundApp));
        updateState(CHANNEL_SCREEN_ORIENTATION, toDecimal(deviceInfo.screenOrientation));
        updateState(CHANNEL_KIOSK_MODE, toOnOff(deviceInfo.kioskMode));
        updateState(CHANNEL_DEVICE_ADMIN, toOnOff(deviceInfo.isDeviceAdmin));
        updateState(CHANNEL_STORAGE_FREE, toQuantity(deviceInfo.internalStorageFreeSpace, Units.BYTE));
        updateState(CHANNEL_STORAGE_TOTAL, toQuantity(deviceInfo.internalStorageTotalSpace, Units.BYTE));
        updateState(CHANNEL_MEMORY_FREE, toQuantity(deviceInfo.ramFreeMemory, Units.BYTE));
        updateState(CHANNEL_MEMORY_TOTAL, toQuantity(deviceInfo.ramTotalMemory, Units.BYTE));
    }

    private static State toDecimal(@Nullable Double value) {
        return value == null ? UnDefType.UNDEF : new DecimalType(value);
    }

    private static State toOnOff(@Nullable Boolean value) {
        return value == null ? UnDefType.UNDEF : OnOffType.from(value);
    }

    private static State toString(@Nullable String value) {
        return value == null ? UnDefType.UNDEF : new StringType(value);
    }

    private static <Q extends Quantity<Q>> State toQuantity(@Nullable Double value, Unit<Q> unit) {
        return value == null ? UnDefType.UNDEF : new QuantityType<>(value, unit);
    }

    /**
     * Builds the Thing properties describing the device from a {@code deviceInfo} response.
     */
    static Map<String, String> buildProperties(DeviceInfo deviceInfo) {
        Map<String, String> properties = new HashMap<>();
        putIfPresent(properties, Thing.PROPERTY_VENDOR, deviceInfo.deviceManufacturer);
        putIfPresent(properties, Thing.PROPERTY_MODEL_ID, deviceInfo.deviceModel);
        putIfPresent(properties, PROPERTY_DEVICE_ID, deviceInfo.deviceID);
        putIfPresent(properties, PROPERTY_DEVICE_NAME, deviceInfo.deviceName);
        putIfPresent(properties, PROPERTY_APP_VERSION, deviceInfo.appVersionName);
        String mac = deviceInfo.mac;
        if (mac != null && !PLACEHOLDER_MAC.equalsIgnoreCase(mac)) {
            putIfPresent(properties, Thing.PROPERTY_MAC_ADDRESS, mac);
        }
        return properties;
    }

    private static void putIfPresent(Map<String, String> properties, String key, @Nullable String value) {
        if (value != null && !value.isBlank()) {
            properties.put(key, value);
        }
    }

    /**
     * Sends a command to the Fully Kiosk REST interface and parses the JSON response.
     *
     * @param config the configuration to use for this request
     * @param fullyCommand the value of the {@code cmd} query parameter
     * @param parameters additional query parameters
     * @param responseType the class to parse the JSON response into
     */
    private <T extends Response> T sendCommand(FullyKioskConfiguration config, String fullyCommand,
            Map<String, String> parameters, Class<T> responseType)
            throws FullyKioskCommunicationException, InterruptedException {
        // Jetty URL-encodes query parameters, so values such as the password may contain any characters.
        Request request = httpClient.newRequest(config.hostname, config.port).path("/").param("cmd", fullyCommand)
                .param("type", "json").param("password", config.password)
                .timeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        parameters.forEach(request::param);

        ContentResponse response;
        try {
            response = request.send();
        } catch (TimeoutException | ExecutionException e) {
            logger.debug("Request '{}' to Fully Kiosk at {}:{} failed: {}", fullyCommand, config.hostname, config.port,
                    e.getMessage());
            throw new FullyKioskCommunicationException("@text/offline.communication-error.unreachable", e);
        }

        if (response.getStatus() != HttpStatus.OK_200) {
            throw new FullyKioskCommunicationException(
                    "@text/offline.communication-error.http-status [\"" + response.getStatus() + "\"]");
        }
        return parseResponse(response.getContentAsString(), responseType);
    }

    /**
     * Parses a JSON response and rejects error responses reported by the device.
     */
    static <T extends Response> T parseResponse(String json, Class<T> responseType)
            throws FullyKioskCommunicationException {
        @Nullable
        T response;
        try {
            response = GSON.fromJson(json, responseType);
        } catch (JsonSyntaxException e) {
            throw new FullyKioskCommunicationException("@text/offline.communication-error.invalid-response", e);
        }
        if (response == null) {
            throw new FullyKioskCommunicationException("@text/offline.communication-error.invalid-response");
        }
        if (Response.STATUS_ERROR.equals(response.status)) {
            String statusText = response.statustext;
            throw new FullyKioskDeviceErrorException(statusText == null ? "" : statusText);
        }
        return response;
    }
}
