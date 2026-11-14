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

import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * A Fully Kiosk REST command: the value of the {@code cmd} query parameter and its additional parameters.
 *
 * @author Keith Knauss - Initial contribution
 */
@NonNullByDefault
record FullyCommand(String name, Map<String, String> parameters) {

    FullyCommand(String name) {
        this(name, Map.of());
    }
}
