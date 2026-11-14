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

import static org.junit.jupiter.api.Assertions.*;
import static org.openhab.binding.fullykiosk.internal.FullyKioskBindingConstants.*;

import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.jupiter.api.Test;
import org.openhab.binding.fullykiosk.internal.dto.DeviceInfo;
import org.openhab.binding.fullykiosk.internal.dto.Response;
import org.openhab.core.thing.Thing;

/**
 * Tests parsing of Fully Kiosk responses and derived Thing properties.
 *
 * @author Keith Knauss - Initial contribution
 */
@NonNullByDefault
public class FullyKioskHandlerTest {

    private static DeviceInfo parseDeviceInfo(String json) throws FullyKioskCommunicationException {
        return FullyKioskHandler.parseResponse(json, DeviceInfo.class);
    }

    @Test
    public void parsesBatteryLevel() throws FullyKioskCommunicationException {
        DeviceInfo deviceInfo = parseDeviceInfo("{\"deviceName\":\"Garage\",\"batteryLevel\":65,\"plugged\":true}");

        assertEquals(65.0, deviceInfo.batteryLevel);
    }

    @Test
    public void parsesScreenState() throws FullyKioskCommunicationException {
        DeviceInfo deviceInfo = parseDeviceInfo("{\"screenOn\":true,\"isInScreensaver\":false}");

        assertEquals(Boolean.TRUE, deviceInfo.screenOn);
        assertEquals(Boolean.FALSE, deviceInfo.isInScreensaver);
    }

    @Test
    public void parsesDiagnosticFields() throws FullyKioskCommunicationException {
        DeviceInfo deviceInfo = parseDeviceInfo("""
                {
                  "batteryTemperature": 23.5,
                  "plugged": false,
                  "currentPage": "http://openhab:8080/page/tablet",
                  "foregroundApp": "de.ozerov.fully",
                  "screenOrientation": 90,
                  "kioskMode": true,
                  "isDeviceAdmin": true,
                  "internalStorageFreeSpace": 12345678901,
                  "internalStorageTotalSpace": 32000000000,
                  "ramFreeMemory": 1500000000.5,
                  "ramTotalMemory": 3000000000
                }
                """);

        assertEquals(23.5, deviceInfo.batteryTemperature);
        assertEquals(Boolean.FALSE, deviceInfo.plugged);
        assertEquals("http://openhab:8080/page/tablet", deviceInfo.currentPage);
        assertEquals("de.ozerov.fully", deviceInfo.foregroundApp);
        assertEquals(90.0, deviceInfo.screenOrientation);
        assertEquals(Boolean.TRUE, deviceInfo.kioskMode);
        assertEquals(Boolean.TRUE, deviceInfo.isDeviceAdmin);
        assertEquals(12345678901.0, deviceInfo.internalStorageFreeSpace);
        assertEquals(32000000000.0, deviceInfo.internalStorageTotalSpace);
        assertEquals(1500000000.5, deviceInfo.ramFreeMemory);
        assertEquals(3000000000.0, deviceInfo.ramTotalMemory);
    }

    @Test
    public void missingFieldsAreNull() throws FullyKioskCommunicationException {
        DeviceInfo deviceInfo = parseDeviceInfo("{\"deviceName\":\"Garage\"}");

        assertNull(deviceInfo.batteryLevel);
        assertNull(deviceInfo.screenOn);
        assertNull(deviceInfo.plugged);
        assertNull(deviceInfo.currentPage);
        assertNull(deviceInfo.internalStorageFreeSpace);
    }

    @Test
    public void buildsProperties() throws FullyKioskCommunicationException {
        DeviceInfo deviceInfo = parseDeviceInfo("""
                {
                  "deviceID": "abc123",
                  "deviceName": "Garage Tablet",
                  "deviceManufacturer": "samsung",
                  "deviceModel": "SM-T290",
                  "appVersionName": "1.57",
                  "Mac": "a4:50:46:12:34:56"
                }
                """);

        assertEquals(
                Map.of(Thing.PROPERTY_VENDOR, "samsung", Thing.PROPERTY_MODEL_ID, "SM-T290", PROPERTY_DEVICE_ID,
                        "abc123", PROPERTY_DEVICE_NAME, "Garage Tablet", PROPERTY_APP_VERSION, "1.57",
                        Thing.PROPERTY_MAC_ADDRESS, "a4:50:46:12:34:56"),
                FullyKioskHandler.buildProperties(deviceInfo));
    }

    @Test
    public void omitsPlaceholderMacAndBlankProperties() throws FullyKioskCommunicationException {
        DeviceInfo deviceInfo = parseDeviceInfo("{\"deviceModel\":\"\",\"Mac\":\"02:00:00:00:00:00\"}");

        assertEquals(Map.of(), FullyKioskHandler.buildProperties(deviceInfo));
    }

    @Test
    public void acceptsCommandResponse() throws FullyKioskCommunicationException {
        Response response = FullyKioskHandler
                .parseResponse("{\"status\":\"OK\",\"statustext\":\"Switching the screen on\"}", Response.class);

        assertEquals("OK", response.status);
    }

    @Test
    public void errorResponseIsRejected() {
        FullyKioskDeviceErrorException e = assertThrows(FullyKioskDeviceErrorException.class,
                () -> parseDeviceInfo("{\"status\":\"Error\",\"statustext\":\"Wrong \\\"pw\\\"\"}"));

        assertEquals("Wrong \"pw\"", e.getDeviceMessage());
        assertEquals("@text/offline.communication-error.device-error [\"Wrong 'pw'\"]", e.getMessage());
    }

    @Test
    public void malformedResponseIsRejected() {
        assertThrows(FullyKioskCommunicationException.class, () -> parseDeviceInfo("<html>"));
    }

    @Test
    public void emptyResponseIsRejected() {
        assertThrows(FullyKioskCommunicationException.class, () -> parseDeviceInfo(""));
    }
}
