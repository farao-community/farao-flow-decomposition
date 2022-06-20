/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.flow_decomposition;

import com.powsybl.iidm.network.Country;

import java.util.Map;

/**
 * @author Sebastien Murgey {@literal <sebastien.murgey at rte-france.com>}
 * @author Hugo Schindler {@literal <hugo.schindler at rte-france.com>}
 */
class IntermediateFlowDecompositionResults {
    private Map<Country, Map<String, Double>> glsks;
    private SparseMatrixWithIndexesTriplet nodalInjectionsMatrix;
    private SparseMatrixWithIndexesTriplet ptdfMatrix;

    IntermediateFlowDecompositionResults() {
    }

    public Map<Country, Map<String, Double>> getGlsks() {
        return glsks;
    }

    void setGlsks(Map<Country, Map<String, Double>> glsks) {
        this.glsks = glsks;
    }

    public Map<String, Map<String, Double>> getNodalInjectionsMap() {
        return nodalInjectionsMatrix.toMap();
    }

    void setNodalInjectionsMatrix(SparseMatrixWithIndexesTriplet nodalInjectionsMatrix) {
        this.nodalInjectionsMatrix = nodalInjectionsMatrix;
    }

    public Map<String, Map<String, Double>> getPtdfMap() {
        return ptdfMatrix.toMap();
    }

    void setPtdfMatrix(SparseMatrixWithIndexesTriplet ptdfMatrix) {
        this.ptdfMatrix = ptdfMatrix;
    }
}
