/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.flow_decomposition;

import java.util.Map;

/**
 * @author Sebastien Murgey {@literal <sebastien.murgey at rte-france.com>}
 * @author Hugo Schindler {@literal <hugo.schindler at rte-france.com>}
 */
abstract class AbstractSparseMatrixWithIndexes {
    private static final boolean DO_NOT_FILL_ZEROS = false;
    protected final Map<String, Integer> rowIndex;
    protected final Map<String, Integer> colIndex;

    protected AbstractSparseMatrixWithIndexes(Map<String, Integer> rowIndex, Map<String, Integer> colIndex) {
        this.rowIndex = rowIndex;
        this.colIndex = colIndex;
    }

    abstract Map<String, Map<String, Double>> toMap(boolean fillZeros);

    Map<String, Map<String, Double>> toMap() {
        return toMap(DO_NOT_FILL_ZEROS);
    }
}
