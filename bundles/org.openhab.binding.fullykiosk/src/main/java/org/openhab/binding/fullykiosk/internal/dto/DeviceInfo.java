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
package org.openhab.binding.fullykiosk.internal.dto;

import org.eclipse.jdt.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

/**
 * The {@link DeviceInfo} is the JSON response of the Fully Kiosk Browser REST command {@code cmd=deviceInfo}.
 *
 * @author Keith Knauss - Initial contribution
 */
public class DeviceInfo extends Response {
    public @Nullable String deviceID;
    public @Nullable String deviceName;
    public @Nullable String deviceManufacturer;
    public @Nullable String deviceModel;
    public @Nullable String appVersionName;
    @SerializedName("Mac")
    public @Nullable String mac;

    public @Nullable Boolean screenOn;
    public @Nullable Boolean isInScreensaver;

    /** Battery charge in percent (0-100). */
    public @Nullable Double batteryLevel;
    /** Battery temperature in degrees Celsius. */
    public @Nullable Double batteryTemperature;
    public @Nullable Boolean plugged;

    public @Nullable String currentPage;
    public @Nullable String foregroundApp;
    public @Nullable Double screenOrientation;
    public @Nullable Boolean kioskMode;
    public @Nullable Boolean isDeviceAdmin;

    // Numeric fields use Double so that an unexpected decimal value cannot fail parsing of the whole response.
    /** Free internal storage in bytes. */
    public @Nullable Double internalStorageFreeSpace;
    /** Total internal storage in bytes. */
    public @Nullable Double internalStorageTotalSpace;
    /** Free RAM in bytes. */
    public @Nullable Double ramFreeMemory;
    /** Total RAM in bytes. */
    public @Nullable Double ramTotalMemory;
}
