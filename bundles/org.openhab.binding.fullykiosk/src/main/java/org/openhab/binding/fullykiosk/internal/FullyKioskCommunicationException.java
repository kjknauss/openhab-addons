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
 * The {@link FullyKioskCommunicationException} is thrown when a request to the Fully Kiosk REST interface fails.
 * Its message is a Thing status description, usually an {@code @text/} i18n reference.
 *
 * @author Keith Knauss - Initial contribution
 */
@NonNullByDefault
public class FullyKioskCommunicationException extends Exception {

    private static final long serialVersionUID = 1L;

    public FullyKioskCommunicationException(String statusDescription) {
        super(statusDescription);
    }

    public FullyKioskCommunicationException(String statusDescription, Throwable cause) {
        super(statusDescription, cause);
    }
}
