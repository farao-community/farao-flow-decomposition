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
public class DecompositionRescaler {
    public DecomposedFlow rescale(DecomposedFlow decomposedFlow) {
        DecomposedFlow noRelievingScaledDecomposedFlow = new DecomposedFlow(decomposedFlow);
        noRelievingScaledDecomposedFlow.replaceRelievingFlows();
        double scaleFactor = (decomposedFlow.getAcReferenceFlow() - decomposedFlow.getReferenceOrientedTotalFlow())
            / noRelievingScaledDecomposedFlow.getReferenceOrientedTotalFlow();
        DecomposedFlow rescaledDecomposedFlow = new DecomposedFlow(decomposedFlow);
        rescaledDecomposedFlow.sum(noRelievingScaledDecomposedFlow.scale(scaleFactor));
        return rescaledDecomposedFlow;
    }

    public FlowDecompositionResults rescale(FlowDecompositionResults flowDecompositionResults) {
        FlowDecompositionResults newFlowDecompositionResults = flowDecompositionResults.copy();
        Map<String, DecomposedFlow> decomposedFlowsMap = newFlowDecompositionResults.getDecomposedFlowsMap();
        decomposedFlowsMap.forEach((s, decomposedFlow) -> decomposedFlowsMap.put(s, rescale(decomposedFlow)));
        return newFlowDecompositionResults;
    }
}