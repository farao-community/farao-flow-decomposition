/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.flow_decomposition;

/**
 * This class provides a flow decomposition rescaler for a single XNEC decomposition.
 *
 * @author Sebastien Murgey {@literal <sebastien.murgey at rte-france.com>}
 * @author Hugo Schindler {@literal <hugo.schindler at rte-france.com>}
 */
public class DecomposedFlowRescaler {
    /**
     * This implementation will remove relieving flows and scale the new total sum to the AC reference flow.
     *
     * @param decomposedFlow Unscaled flow decomposition
     * @return Rescaled flow decomposition
     */
    public DecomposedFlow rescale(DecomposedFlow decomposedFlow) {
        DecomposedFlow rescaledDecomposedFlow = new DecomposedFlow(decomposedFlow);
        rescaledDecomposedFlow.replaceRelievingFlows();
        Double totalFlow = rescaledDecomposedFlow.getTotalFlow();
        rescaledDecomposedFlow.scale(decomposedFlow.getAcReferenceFlow() / totalFlow);
        return rescaledDecomposedFlow;
    }
}
