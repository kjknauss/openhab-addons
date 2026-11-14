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
 * The {@link FullyKioskConfiguration} class contains fields mapping thing configuration parameters.
 *
 * @author Keith Knauss - Initial contribution
 */
@NonNullByDefault
public class FullyKioskConfiguration {

    public String hostname = "";
    public int port = 2323;
    public String password = "";
    public int refreshInterval = 30;
}
