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
import java.util.Set;
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
import org.openhab.core.library.types.PercentType;
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
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
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

    private static final String SETTING_SCREEN_BRIGHTNESS = "screenBrightness";
    private static final String SETTING_SCREENSAVER_BRIGHTNESS = "screensaverBrightness";
    private static final String SETTING_MOTION_DETECTION = "motionDetection";
    private static final String SETTING_SCREENSAVER_TIMEOUT = "timeToScreensaverV2";
    private static final String SETTING_SCREEN_OFF_TIMEOUT = "timeToScreenOffV2";
    private static final int MAX_BRIGHTNESS = 255;
    private static final int MAX_TIMEOUT_SECONDS = 86400;

    /** Channels whose state comes from the settings list rather than from deviceInfo. */
    private static final Set<String> SETTINGS_CHANNELS = Set.of(CHANNEL_SCREEN_BRIGHTNESS,
            CHANNEL_SCREENSAVER_BRIGHTNESS, CHANNEL_MOTION_DETECTION, CHANNEL_SCREENSAVER_TIMEOUT,
            CHANNEL_SCREEN_OFF_TIMEOUT);

    /** Commands without parameters that may be sent through the action channel. */
    private static final Set<String> ACTIONS = Set.of("loadStartUrl", "toForeground", "toBackground", "restartApp",
            "clearCache", "triggerMotion");

    private final Logger logger = LoggerFactory.getLogger(FullyKioskHandler.class);
    private final HttpClient httpClient;

    private volatile FullyKioskConfiguration config = new FullyKioskConfiguration();
    private volatile @Nullable DeviceInfo lastDeviceInfo;
    private @Nullable ScheduledFuture<?> pollingJob;
    private volatile Map<String, String> lastProperties = Map.of();
    private volatile @Nullable JsonObject lastSettings;
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
        lastSettings = null;
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (command instanceof RefreshType) {
            if (SETTINGS_CHANNELS.contains(channelUID.getId())) {
                updateSettingsChannels(lastSettings);
                return;
            }
            DeviceInfo deviceInfo = lastDeviceInfo;
            if (deviceInfo != null) {
                updateChannels(deviceInfo);
            }
            return;
        }

        FullyCommand fullyCommand = toFullyCommand(channelUID.getId(), command);
        if (fullyCommand == null) {
            logger.debug("Unsupported command {} for channel {}", command, channelUID.getId());
            return;
        }
        FullyCommand commandToSend = fullyCommand;
        scheduler.execute(() -> executeCommand(commandToSend));
    }

    /**
     * Translates an openHAB command into a Fully Kiosk REST command.
     *
     * @return the command to send, or {@code null} if the channel does not support the command
     */
    static @Nullable FullyCommand toFullyCommand(String channelId, Command command) {
        switch (channelId) {
            case CHANNEL_SCREEN -> {
                if (command instanceof OnOffType onOff) {
                    return new FullyCommand(onOff == OnOffType.ON ? "screenOn" : "screenOff");
                }
            }
            case CHANNEL_SCREENSAVER -> {
                if (command instanceof OnOffType onOff) {
                    return new FullyCommand(onOff == OnOffType.ON ? "startScreensaver" : "stopScreensaver");
                }
            }
            case CHANNEL_SCREEN_BRIGHTNESS -> {
                return toBrightnessCommand(SETTING_SCREEN_BRIGHTNESS, command);
            }
            case CHANNEL_SCREENSAVER_BRIGHTNESS -> {
                return toBrightnessCommand(SETTING_SCREENSAVER_BRIGHTNESS, command);
            }
            case CHANNEL_KIOSK_LOCKED -> {
                if (command instanceof OnOffType onOff) {
                    return new FullyCommand(onOff == OnOffType.ON ? "lockKiosk" : "unlockKiosk");
                }
            }
            case CHANNEL_MAINTENANCE_MODE -> {
                if (command instanceof OnOffType onOff) {
                    return new FullyCommand(onOff == OnOffType.ON ? "enableLockedMode" : "disableLockedMode");
                }
            }
            case CHANNEL_MOTION_DETECTION -> {
                if (command instanceof OnOffType onOff) {
                    return new FullyCommand("setBooleanSetting",
                            Map.of("key", SETTING_MOTION_DETECTION, "value", String.valueOf(onOff == OnOffType.ON)));
                }
            }
            case CHANNEL_SCREENSAVER_TIMEOUT -> {
                return toTimeoutCommand(SETTING_SCREENSAVER_TIMEOUT, command);
            }
            case CHANNEL_SCREEN_OFF_TIMEOUT -> {
                return toTimeoutCommand(SETTING_SCREEN_OFF_TIMEOUT, command);
            }
            case CHANNEL_URL -> {
                if (command instanceof StringType url && !url.toString().isBlank()) {
                    return new FullyCommand("loadUrl", Map.of("url", url.toString()));
                }
            }
            case CHANNEL_ACTION -> {
                // Only a fixed set of commands is allowed, so arbitrary REST commands cannot be sent via this channel.
                if (command instanceof StringType action && ACTIONS.contains(action.toString())) {
                    return new FullyCommand(action.toString());
                }
            }
            default -> {
            }
        }
        return null;
    }

    private static @Nullable FullyCommand toBrightnessCommand(String settingKey, Command command) {
        Integer brightness = null;
        if (command instanceof PercentType percent) {
            brightness = toFullyBrightness(percent);
        } else if (command instanceof OnOffType onOff) {
            brightness = onOff == OnOffType.ON ? MAX_BRIGHTNESS : 0;
        }
        return brightness == null ? null : setStringSetting(settingKey, brightness);
    }

    private static @Nullable FullyCommand toTimeoutCommand(String settingKey, Command command) {
        Integer seconds = toTimeoutSeconds(command);
        return seconds == null ? null : setStringSetting(settingKey, seconds);
    }

    private static FullyCommand setStringSetting(String key, int value) {
        return new FullyCommand("setStringSetting", Map.of("key", key, "value", String.valueOf(value)));
    }

    /**
     * Converts a timeout command into whole seconds. Plain numbers are taken as seconds.
     *
     * @return the timeout in seconds, or {@code null} if the command is not a valid timeout
     */
    static @Nullable Integer toTimeoutSeconds(Command command) {
        double seconds;
        if (command instanceof QuantityType<?> quantity) {
            QuantityType<?> inSeconds = quantity.toUnit(Units.SECOND);
            if (inSeconds == null) {
                return null;
            }
            seconds = inSeconds.doubleValue();
        } else if (command instanceof DecimalType decimal) {
            seconds = decimal.doubleValue();
        } else {
            return null;
        }
        long rounded = Math.round(seconds);
        return rounded < 0 || rounded > MAX_TIMEOUT_SECONDS ? null : (int) rounded;
    }

    /**
     * Converts a percentage into the Fully Kiosk brightness range 0-255.
     */
    static int toFullyBrightness(PercentType percent) {
        return (int) Math.round(percent.doubleValue() * MAX_BRIGHTNESS / 100);
    }

    /**
     * Converts a Fully Kiosk brightness (0-255) into a percentage.
     */
    static PercentType toBrightnessPercent(double brightness) {
        double clamped = Math.max(0, Math.min(MAX_BRIGHTNESS, brightness));
        return new PercentType((int) Math.round(clamped * 100 / MAX_BRIGHTNESS));
    }

    private void executeCommand(FullyCommand fullyCommand) {
        if (!active) {
            return;
        }
        try {
            sendCommand(config, fullyCommand.name(), fullyCommand.parameters(), Response.class);
        } catch (FullyKioskDeviceErrorException e) {
            logger.warn("Fully Kiosk rejected command '{}': {}", fullyCommand.name(), e.getDeviceMessage());
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
        FullyKioskConfiguration config = this.config;
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

            // The settings list is much larger than deviceInfo, so fetch it only when a channel needs it.
            if (SETTINGS_CHANNELS.stream().anyMatch(this::isLinked)) {
                updateSettings(config);
            }
        } catch (FullyKioskCommunicationException e) {
            updateStatus(ThingStatusDetail.COMMUNICATION_ERROR, e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void updateSettings(FullyKioskConfiguration config)
            throws FullyKioskCommunicationException, InterruptedException {
        @Nullable
        JsonObject settings = null;
        try {
            settings = parseSettings(sendRequest(config, "listSettings", Map.of()));
        } catch (FullyKioskDeviceErrorException e) {
            logger.debug("Fully Kiosk rejected reading settings: {}", e.getDeviceMessage());
        }
        lastSettings = settings;
        updateSettingsChannels(settings);
    }

    private void updateSettingsChannels(@Nullable JsonObject settings) {
        updateState(CHANNEL_SCREEN_BRIGHTNESS,
                toBrightnessState(getNumberSetting(settings, SETTING_SCREEN_BRIGHTNESS)));
        updateState(CHANNEL_SCREENSAVER_BRIGHTNESS,
                toBrightnessState(getNumberSetting(settings, SETTING_SCREENSAVER_BRIGHTNESS)));
        updateState(CHANNEL_MOTION_DETECTION, toOnOff(getBooleanSetting(settings, SETTING_MOTION_DETECTION)));
        updateState(CHANNEL_SCREENSAVER_TIMEOUT,
                toQuantity(getNumberSetting(settings, SETTING_SCREENSAVER_TIMEOUT), Units.SECOND));
        updateState(CHANNEL_SCREEN_OFF_TIMEOUT,
                toQuantity(getNumberSetting(settings, SETTING_SCREEN_OFF_TIMEOUT), Units.SECOND));
    }

    private static State toBrightnessState(@Nullable Double brightness) {
        return brightness == null ? UnDefType.UNDEF : toBrightnessPercent(brightness);
    }

    private void updateChannels(DeviceInfo deviceInfo) {
        updateState(CHANNEL_SCREEN, toOnOff(deviceInfo.screenOn));
        updateState(CHANNEL_SCREENSAVER, toOnOff(deviceInfo.isInScreensaver));
        updateState(CHANNEL_BATTERY_LEVEL, toQuantity(deviceInfo.batteryLevel, Units.PERCENT));
        updateState(CHANNEL_BATTERY_TEMPERATURE, toQuantity(deviceInfo.batteryTemperature, SIUnits.CELSIUS));
        updateState(CHANNEL_PLUGGED, toOnOff(deviceInfo.plugged));
        updateState(CHANNEL_URL, toString(deviceInfo.currentPage));
        updateState(CHANNEL_FOREGROUND_APP, toString(deviceInfo.foregroundApp));
        updateState(CHANNEL_SCREEN_ORIENTATION, toDecimal(deviceInfo.screenOrientation));
        updateState(CHANNEL_KIOSK_MODE, toOnOff(deviceInfo.kioskMode));
        updateState(CHANNEL_KIOSK_LOCKED, toOnOff(deviceInfo.kioskLocked));
        updateState(CHANNEL_MAINTENANCE_MODE, toOnOff(deviceInfo.maintenanceMode));
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
        return parseResponse(sendRequest(config, fullyCommand, parameters), responseType);
    }

    /**
     * Sends a command to the Fully Kiosk REST interface.
     *
     * @return the response body
     */
    private String sendRequest(FullyKioskConfiguration config, String fullyCommand, Map<String, String> parameters)
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
        return response.getContentAsString();
    }

    /**
     * Parses a {@code listSettings} response, a flat JSON object of setting keys and values.
     */
    static JsonObject parseSettings(String json) throws FullyKioskCommunicationException {
        // Validates the JSON object and rejects error responses.
        parseResponse(json, Response.class);
        return JsonParser.parseString(json).getAsJsonObject();
    }

    /**
     * Reads a numeric setting. Fully Kiosk stores most settings as strings, so numeric strings are accepted.
     *
     * @return the value, or {@code null} if the setting is missing, empty or not numeric
     */
    static @Nullable Double getNumberSetting(@Nullable JsonObject settings, String key) {
        if (settings == null) {
            return null;
        }
        JsonElement element = settings.get(key);
        if (element == null || !element.isJsonPrimitive()) {
            return null;
        }
        JsonPrimitive primitive = element.getAsJsonPrimitive();
        if (primitive.isNumber()) {
            return primitive.getAsDouble();
        }
        if (primitive.isString()) {
            try {
                return Double.valueOf(primitive.getAsString().trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /**
     * Reads a boolean setting. Both JSON booleans and the strings {@code "true"} and {@code "false"} are accepted.
     *
     * @return the value, or {@code null} if the setting is missing or not a boolean
     */
    static @Nullable Boolean getBooleanSetting(@Nullable JsonObject settings, String key) {
        if (settings == null) {
            return null;
        }
        JsonElement element = settings.get(key);
        if (element == null || !element.isJsonPrimitive()) {
            return null;
        }
        JsonPrimitive primitive = element.getAsJsonPrimitive();
        if (primitive.isBoolean()) {
            return primitive.getAsBoolean();
        }
        if (primitive.isString()) {
            String value = primitive.getAsString().trim();
            if ("true".equalsIgnoreCase(value)) {
                return Boolean.TRUE;
            }
            if ("false".equalsIgnoreCase(value)) {
                return Boolean.FALSE;
            }
        }
        return null;
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
