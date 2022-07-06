package com.farao_community.farao.flow_decomposition;

import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.PhaseTapChanger;
import com.powsybl.iidm.network.PhaseTapChangerStep;

import java.util.List;
import java.util.Map;
import java.util.Optional;

class PstFlowComputer {
    private static final String PST_COLUMN_NAME = "PST Flow";

    SparseMatrixWithIndexesCSC getPstFlowMatrix(Network network, List<String> pstList, Map<String, Integer> pstIndex, SparseMatrixWithIndexesTriplet psdfMatrix) {
        SparseMatrixWithIndexesTriplet deltaTapMatrix = getDeltaTapMatrix(network, pstList, pstIndex);
        return SparseMatrixWithIndexesCSC.mult(psdfMatrix.toCSCMatrix(), deltaTapMatrix.toCSCMatrix());
    }

    private SparseMatrixWithIndexesTriplet getDeltaTapMatrix(Network network, List<String> pstList, Map<String, Integer> pstIndex) {
        SparseMatrixWithIndexesTriplet deltaTapMatrix = new SparseMatrixWithIndexesTriplet(pstIndex, PST_COLUMN_NAME, pstIndex.size());
        for (String pst: pstList) {
            PhaseTapChanger phaseTapChanger = network.getTwoWindingsTransformer(pst).getPhaseTapChanger();
            Optional<PhaseTapChangerStep> neutralStep = phaseTapChanger.getNeutralStep();
            double deltaTap = 0.0;
            if (neutralStep.isPresent()) {
                deltaTap = phaseTapChanger.getCurrentStep().getAlpha() - neutralStep.get().getAlpha();
            }
            deltaTapMatrix.addItem(pst, PST_COLUMN_NAME, deltaTap);
        }
        return deltaTapMatrix;
    }

}
