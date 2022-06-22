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
class SparseMatrixWithIndexes {
    protected Map<String, Integer> rowIndex;
    protected Map<String, Integer> colIndex;

    protected SparseMatrixWithIndexes(Map<String, Integer> rowIndex, Map<String, Integer> colIndex) {
        this.rowIndex = rowIndex;
        this.colIndex = colIndex;
    }
}
