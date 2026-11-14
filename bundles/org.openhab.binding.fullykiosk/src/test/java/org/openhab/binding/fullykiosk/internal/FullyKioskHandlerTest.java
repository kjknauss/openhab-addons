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

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.jupiter.api.Test;
import org.openhab.binding.fullykiosk.internal.dto.DeviceInfo;

/**
 * Tests parsing of Fully Kiosk {@code deviceInfo} responses.
 *
 * @author Keith Knauss - Initial contribution
 */
@NonNullByDefault
public class FullyKioskHandlerTest {

    @Test
    public void parsesBatteryLevel() throws FullyKioskCommunicationException {
        DeviceInfo deviceInfo = FullyKioskHandler
                .parseDeviceInfo("{\"deviceName\":\"Garage\",\"batteryLevel\":65,\"plugged\":true}");

        assertEquals(65.0, deviceInfo.batteryLevel);
    }

    @Test
    public void missingBatteryLevelIsNull() throws FullyKioskCommunicationException {
        DeviceInfo deviceInfo = FullyKioskHandler.parseDeviceInfo("{\"deviceName\":\"Garage\"}");

        assertNull(deviceInfo.batteryLevel);
    }

    @Test
    public void errorResponseIsRejected() {
        FullyKioskCommunicationException e = assertThrows(FullyKioskCommunicationException.class,
                () -> FullyKioskHandler.parseDeviceInfo("{\"status\":\"Error\",\"statustext\":\"Wrong \\\"pw\\\"\"}"));

        assertEquals("@text/offline.communication-error.device-error [\"Wrong 'pw'\"]", e.getMessage());
    }

    @Test
    public void malformedResponseIsRejected() {
        assertThrows(FullyKioskCommunicationException.class, () -> FullyKioskHandler.parseDeviceInfo("<html>"));
    }

    @Test
    public void emptyResponseIsRejected() {
        assertThrows(FullyKioskCommunicationException.class, () -> FullyKioskHandler.parseDeviceInfo(""));
    }
}
