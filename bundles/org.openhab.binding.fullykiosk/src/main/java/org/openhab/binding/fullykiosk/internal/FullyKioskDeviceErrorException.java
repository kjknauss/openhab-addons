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

/**
 * The {@link FullyKioskDeviceErrorException} is thrown when the device was reached but answered with
 * {@code status} {@code "Error"}, for example because of a wrong password or a rejected command.
 *
 * @author Keith Knauss - Initial contribution
 */
@NonNullByDefault
public class FullyKioskDeviceErrorException extends FullyKioskCommunicationException {

    private static final long serialVersionUID = 1L;

    private final String deviceMessage;

    public FullyKioskDeviceErrorException(String deviceMessage) {
        super("@text/offline.communication-error.device-error [\"" + deviceMessage.replace('"', '\'') + "\"]");
        this.deviceMessage = deviceMessage;
    }

    /**
     * @return the {@code statustext} reported by the device
     */
    public String getDeviceMessage() {
        return deviceMessage;
    }
}
