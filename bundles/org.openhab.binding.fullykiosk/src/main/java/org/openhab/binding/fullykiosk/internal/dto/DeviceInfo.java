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

/**
 * The {@link DeviceInfo} is the JSON response of the Fully Kiosk Browser REST command {@code cmd=deviceInfo}.
 * <p>
 * On failure, Fully Kiosk answers with only {@code status} set to {@code "Error"} and an explanation in
 * {@code statustext}.
 *
 * @author Keith Knauss - Initial contribution
 */
public class DeviceInfo {
    public static final String STATUS_ERROR = "Error";

    public @Nullable String status;
    public @Nullable String statustext;

    /** Battery charge in percent (0-100). */
    public @Nullable Double batteryLevel;
}
