/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.flow_decomposition;

import org.ejml.data.DMatrixSparseCSC;
import org.ejml.data.DMatrixSparseTriplet;
import org.ejml.ops.DConvertMatrixStruct;

import java.util.Map;

/**
 * @author Sebastien Murgey {@literal <sebastien.murgey at rte-france.com>}
 * @author Hugo Schindler {@literal <hugo.schindler at rte-france.com>}
 */
class SparseMatrixWithIndexesTriplet extends AbstractSparseMatrixWithIndexes {
    private final DMatrixSparseTriplet tripletMatrix;

    public SparseMatrixWithIndexesTriplet(Map<String, Integer> rowIndex, Map<String, Integer> colIndex, Integer initLength) {
        super(rowIndex, colIndex);
        this.tripletMatrix = new DMatrixSparseTriplet(rowIndex.size(), colIndex.size(), initLength);
    }

    public SparseMatrixWithIndexesTriplet(Map<String, Integer> rowIndex, String colName, Integer initLength) {
        this(rowIndex, Map.of(colName, 0), initLength);
    }

    public void addItem(String row, String col, double value) {
        if (!Double.isNaN(value)) {
            tripletMatrix.addItem(rowIndex.get(row), colIndex.get(col), value);
        }
    }

    public SparseMatrixWithIndexesCSC toCSCMatrix() {
        DMatrixSparseCSC cscMatrix = DConvertMatrixStruct.convert(tripletMatrix, (DMatrixSparseCSC) null);
        return new SparseMatrixWithIndexesCSC(this.rowIndex, this.colIndex, cscMatrix);
    }

    public Map<String, Map<String, Double>> toMap() {
        return toCSCMatrix().toMap();
    }
}
