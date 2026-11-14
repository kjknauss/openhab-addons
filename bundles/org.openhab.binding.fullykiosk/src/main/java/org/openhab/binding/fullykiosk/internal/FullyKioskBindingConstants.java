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

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.thing.ThingTypeUID;

/**
 * The {@link FullyKioskBindingConstants} class defines common constants, which are
 * used across the whole binding.
 *
 * @author Keith Knauss - Initial contribution
 */
@NonNullByDefault
public class FullyKioskBindingConstants {

    private static final String BINDING_ID = "fullykiosk";

    // List of all Thing Type UIDs
    public static final ThingTypeUID THING_TYPE_DEVICE = new ThingTypeUID(BINDING_ID, "device");

    // List of all Channel ids
    public static final String CHANNEL_SCREEN = "screen";
    public static final String CHANNEL_SCREENSAVER = "screensaver";
    public static final String CHANNEL_BATTERY_LEVEL = "battery-level";
    public static final String CHANNEL_BATTERY_TEMPERATURE = "battery-temperature";
    public static final String CHANNEL_PLUGGED = "plugged";
    public static final String CHANNEL_CURRENT_PAGE = "current-page";
    public static final String CHANNEL_FOREGROUND_APP = "foreground-app";
    public static final String CHANNEL_SCREEN_ORIENTATION = "screen-orientation";
    public static final String CHANNEL_KIOSK_MODE = "kiosk-mode";
    public static final String CHANNEL_DEVICE_ADMIN = "device-admin";
    public static final String CHANNEL_STORAGE_FREE = "storage-free";
    public static final String CHANNEL_STORAGE_TOTAL = "storage-total";
    public static final String CHANNEL_MEMORY_FREE = "memory-free";
    public static final String CHANNEL_MEMORY_TOTAL = "memory-total";

    // List of all Thing property names not defined by openHAB core
    public static final String PROPERTY_DEVICE_ID = "deviceId";
    public static final String PROPERTY_DEVICE_NAME = "deviceName";
    public static final String PROPERTY_APP_VERSION = "appVersion";
}
