package com.farao_community.farao.flow_decomposition;

import com.powsybl.iidm.network.Country;

import java.util.Map;

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
