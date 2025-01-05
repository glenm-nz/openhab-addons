/**
 * Copyright (c) 2010-2025 Contributors to the openHAB project
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
package org.openhab.binding.hdpowerview.internal.dto.requests;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.hdpowerview.internal.dto.ShadePosition;

/**
 * The position of a shade to set
 *
 * @author Andy Lintner - Initial contribution
 */
@NonNullByDefault
class ShadePositions {

    public ShadePosition positions;

    public ShadePositions(ShadePosition position) {
        this.positions = position;
    }
}
