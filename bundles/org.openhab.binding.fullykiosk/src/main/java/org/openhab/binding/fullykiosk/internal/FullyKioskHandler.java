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

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.http.HttpStatus;
import org.openhab.binding.fullykiosk.internal.dto.DeviceInfo;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;
import org.openhab.core.types.UnDefType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

/**
 * The {@link FullyKioskHandler} polls a Fully Kiosk Browser device through its Remote Admin REST interface.
 *
 * @author Keith Knauss - Initial contribution
 */
@NonNullByDefault
public class FullyKioskHandler extends BaseThingHandler {

    private static final long REQUEST_TIMEOUT_SECONDS = 10;
    private static final Gson GSON = new Gson();

    private final Logger logger = LoggerFactory.getLogger(FullyKioskHandler.class);
    private final HttpClient httpClient;

    private volatile FullyKioskConfiguration config = new FullyKioskConfiguration();
    private volatile @Nullable DeviceInfo lastDeviceInfo;
    private @Nullable ScheduledFuture<?> pollingJob;

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

        updateStatus(ThingStatus.UNKNOWN);
        pollingJob = scheduler.scheduleWithFixedDelay(this::poll, 0, config.refreshInterval, TimeUnit.SECONDS);
    }

    @Override
    public void dispose() {
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
        }
    }

    private void poll() {
        try {
            DeviceInfo deviceInfo = fetchDeviceInfo(config);
            lastDeviceInfo = deviceInfo;
            updateStatus(ThingStatus.ONLINE);
            updateChannels(deviceInfo);
        } catch (FullyKioskCommunicationException e) {
            updateStatus(ThingStatusDetail.COMMUNICATION_ERROR, e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void updateChannels(DeviceInfo deviceInfo) {
        Double batteryLevel = deviceInfo.batteryLevel;
        updateState(CHANNEL_BATTERY_LEVEL, batteryLevel == null ? UnDefType.UNDEF : new DecimalType(batteryLevel));
    }

    private DeviceInfo fetchDeviceInfo(FullyKioskConfiguration config)
            throws FullyKioskCommunicationException, InterruptedException {
        ContentResponse response;
        try {
            // Jetty URL-encodes query parameters, so the password may contain any characters.
            response = httpClient.newRequest(config.hostname, config.port).path("/").param("cmd", "deviceInfo")
                    .param("type", "json").param("password", config.password)
                    .timeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS).send();
        } catch (TimeoutException | ExecutionException e) {
            logger.debug("Request to Fully Kiosk at {}:{} failed: {}", config.hostname, config.port, e.getMessage());
            throw new FullyKioskCommunicationException("@text/offline.communication-error.unreachable", e);
        }

        if (response.getStatus() != HttpStatus.OK_200) {
            throw new FullyKioskCommunicationException(
                    "@text/offline.communication-error.http-status [\"" + response.getStatus() + "\"]");
        }
        return parseDeviceInfo(response.getContentAsString());
    }

    /**
     * Parses a {@code deviceInfo} response and rejects error responses reported by the device.
     */
    static DeviceInfo parseDeviceInfo(String json) throws FullyKioskCommunicationException {
        DeviceInfo deviceInfo;
        try {
            deviceInfo = GSON.fromJson(json, DeviceInfo.class);
        } catch (JsonSyntaxException e) {
            throw new FullyKioskCommunicationException("@text/offline.communication-error.invalid-response", e);
        }
        if (deviceInfo == null) {
            throw new FullyKioskCommunicationException("@text/offline.communication-error.invalid-response");
        }
        if (DeviceInfo.STATUS_ERROR.equals(deviceInfo.status)) {
            String statusText = deviceInfo.statustext;
            String detail = statusText == null ? "" : statusText.replace('"', '\'');
            throw new FullyKioskCommunicationException(
                    "@text/offline.communication-error.device-error [\"" + detail + "\"]");
        }
        return deviceInfo;
    }
}
