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
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.PercentType;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.library.unit.Units;

import com.google.gson.JsonObject;

/**
 * Tests translation of openHAB commands into Fully Kiosk commands, brightness conversion and settings parsing.
 *
 * @author Keith Knauss - Initial contribution
 */
@NonNullByDefault
public class FullyKioskCommandTest {

    @Test
    public void mapsScreenAndScreensaver() {
        assertEquals(new FullyCommand("screenOn"), FullyKioskHandler.toFullyCommand(CHANNEL_SCREEN, OnOffType.ON));
        assertEquals(new FullyCommand("screenOff"), FullyKioskHandler.toFullyCommand(CHANNEL_SCREEN, OnOffType.OFF));
        assertEquals(new FullyCommand("startScreensaver"),
                FullyKioskHandler.toFullyCommand(CHANNEL_SCREENSAVER, OnOffType.ON));
        assertEquals(new FullyCommand("stopScreensaver"),
                FullyKioskHandler.toFullyCommand(CHANNEL_SCREENSAVER, OnOffType.OFF));
    }

    @Test
    public void mapsBrightness() {
        assertEquals(new FullyCommand("setStringSetting", Map.of("key", "screenBrightness", "value", "128")),
                FullyKioskHandler.toFullyCommand(CHANNEL_SCREEN_BRIGHTNESS, new PercentType(50)));
        assertEquals(new FullyCommand("setStringSetting", Map.of("key", "screenBrightness", "value", "255")),
                FullyKioskHandler.toFullyCommand(CHANNEL_SCREEN_BRIGHTNESS, OnOffType.ON));
        assertEquals(new FullyCommand("setStringSetting", Map.of("key", "screenBrightness", "value", "0")),
                FullyKioskHandler.toFullyCommand(CHANNEL_SCREEN_BRIGHTNESS, OnOffType.OFF));
    }

    @Test
    public void mapsUrl() {
        assertEquals(new FullyCommand("loadUrl", Map.of("url", "http://openhab:8080/page/tablet")),
                FullyKioskHandler.toFullyCommand(CHANNEL_URL, new StringType("http://openhab:8080/page/tablet")));
        assertNull(FullyKioskHandler.toFullyCommand(CHANNEL_URL, new StringType(" ")));
    }

    @Test
    public void mapsOnlyKnownActions() {
        assertEquals(new FullyCommand("restartApp"),
                FullyKioskHandler.toFullyCommand(CHANNEL_ACTION, new StringType("restartApp")));
        assertNull(FullyKioskHandler.toFullyCommand(CHANNEL_ACTION, new StringType("rebootDevice")));
    }

    @Test
    public void rejectsUnsupportedCommands() {
        assertNull(FullyKioskHandler.toFullyCommand(CHANNEL_SCREEN, new StringType("ON")));
        assertNull(FullyKioskHandler.toFullyCommand(CHANNEL_BATTERY_LEVEL, new DecimalType(50)));
    }

    @Test
    public void convertsBrightnessBothWays() {
        assertEquals(0, FullyKioskHandler.toFullyBrightness(PercentType.ZERO));
        assertEquals(255, FullyKioskHandler.toFullyBrightness(PercentType.HUNDRED));
        assertEquals(PercentType.ZERO, FullyKioskHandler.toBrightnessPercent(0));
        assertEquals(PercentType.HUNDRED, FullyKioskHandler.toBrightnessPercent(255));
        assertEquals(new PercentType(50), FullyKioskHandler.toBrightnessPercent(128));
        assertEquals(PercentType.HUNDRED, FullyKioskHandler.toBrightnessPercent(300));
    }

    @Test
    public void readsNumericSettings() throws FullyKioskCommunicationException {
        JsonObject settings = FullyKioskHandler
                .parseSettings("{\"screenBrightness\":\"128\",\"other\":200,\"empty\":\"\",\"flag\":true}");

        assertEquals(128.0, FullyKioskHandler.getNumberSetting(settings, "screenBrightness"));
        assertEquals(200.0, FullyKioskHandler.getNumberSetting(settings, "other"));
        assertNull(FullyKioskHandler.getNumberSetting(settings, "empty"));
        assertNull(FullyKioskHandler.getNumberSetting(settings, "flag"));
        assertNull(FullyKioskHandler.getNumberSetting(settings, "missing"));
    }

    @Test
    public void mapsKioskLockAndMaintenanceMode() {
        assertEquals(new FullyCommand("lockKiosk"),
                FullyKioskHandler.toFullyCommand(CHANNEL_KIOSK_LOCKED, OnOffType.ON));
        assertEquals(new FullyCommand("unlockKiosk"),
                FullyKioskHandler.toFullyCommand(CHANNEL_KIOSK_LOCKED, OnOffType.OFF));
        assertEquals(new FullyCommand("enableLockedMode"),
                FullyKioskHandler.toFullyCommand(CHANNEL_MAINTENANCE_MODE, OnOffType.ON));
        assertEquals(new FullyCommand("disableLockedMode"),
                FullyKioskHandler.toFullyCommand(CHANNEL_MAINTENANCE_MODE, OnOffType.OFF));
    }

    @Test
    public void mapsMotionDetection() {
        assertEquals(new FullyCommand("setBooleanSetting", Map.of("key", "motionDetection", "value", "true")),
                FullyKioskHandler.toFullyCommand(CHANNEL_MOTION_DETECTION, OnOffType.ON));
        assertEquals(new FullyCommand("setBooleanSetting", Map.of("key", "motionDetection", "value", "false")),
                FullyKioskHandler.toFullyCommand(CHANNEL_MOTION_DETECTION, OnOffType.OFF));
    }

    @Test
    public void mapsScreensaverBrightness() {
        assertEquals(new FullyCommand("setStringSetting", Map.of("key", "screensaverBrightness", "value", "26")),
                FullyKioskHandler.toFullyCommand(CHANNEL_SCREENSAVER_BRIGHTNESS, new PercentType(10)));
    }

    @Test
    public void mapsTimeouts() {
        assertEquals(new FullyCommand("setStringSetting", Map.of("key", "timeToScreensaverV2", "value", "300")),
                FullyKioskHandler.toFullyCommand(CHANNEL_SCREENSAVER_TIMEOUT, new QuantityType<>(5, Units.MINUTE)));
        assertEquals(new FullyCommand("setStringSetting", Map.of("key", "timeToScreenOffV2", "value", "45")),
                FullyKioskHandler.toFullyCommand(CHANNEL_SCREEN_OFF_TIMEOUT, new DecimalType(45)));
    }

    @Test
    public void convertsTimeouts() {
        assertEquals(90, FullyKioskHandler.toTimeoutSeconds(new QuantityType<>(1.5, Units.MINUTE)));
        assertEquals(0, FullyKioskHandler.toTimeoutSeconds(DecimalType.ZERO));
        assertEquals(86400, FullyKioskHandler.toTimeoutSeconds(new QuantityType<>(24, Units.HOUR)));
        assertNull(FullyKioskHandler.toTimeoutSeconds(new QuantityType<>(25, Units.HOUR)));
        assertNull(FullyKioskHandler.toTimeoutSeconds(new DecimalType(-1)));
        assertNull(FullyKioskHandler.toTimeoutSeconds(new QuantityType<>(5, Units.PERCENT)));
        assertNull(FullyKioskHandler.toTimeoutSeconds(OnOffType.ON));
    }

    @Test
    public void readsBooleanSettings() throws FullyKioskCommunicationException {
        JsonObject settings = FullyKioskHandler
                .parseSettings("{\"a\":true,\"b\":\"false\",\"c\":\"TRUE\",\"d\":\"yes\",\"e\":1}");

        assertEquals(Boolean.TRUE, FullyKioskHandler.getBooleanSetting(settings, "a"));
        assertEquals(Boolean.FALSE, FullyKioskHandler.getBooleanSetting(settings, "b"));
        assertEquals(Boolean.TRUE, FullyKioskHandler.getBooleanSetting(settings, "c"));
        assertNull(FullyKioskHandler.getBooleanSetting(settings, "d"));
        assertNull(FullyKioskHandler.getBooleanSetting(settings, "e"));
        assertNull(FullyKioskHandler.getBooleanSetting(settings, "missing"));
        assertNull(FullyKioskHandler.getBooleanSetting(null, "a"));
    }

    @Test
    public void rejectsSettingsError() {
        assertThrows(FullyKioskDeviceErrorException.class,
                () -> FullyKioskHandler.parseSettings("{\"status\":\"Error\",\"statustext\":\"Wrong password\"}"));
    }
}
