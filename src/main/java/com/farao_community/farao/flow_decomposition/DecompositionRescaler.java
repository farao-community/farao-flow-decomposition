/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.flow_decomposition;

import java.util.Map;
import java.util.TreeMap;

/**
 * @author Sebastien Murgey {@literal <sebastien.murgey at rte-france.com>}
 * @author Hugo Schindler {@literal <hugo.schindler at rte-france.com>}
 */
class DecompositionRescaler {
    DecomposedFlow rescale(DecomposedFlow decomposedFlow) {
        DecomposedFlow noRelievingScaledDecomposedFlow = new DecomposedFlow(decomposedFlow);
        noRelievingScaledDecomposedFlow.replaceRelievingFlows();
        double scaleFactor = (decomposedFlow.getAcReferenceFlow() - decomposedFlow.getReferenceOrientedTotalFlow())
            / noRelievingScaledDecomposedFlow.getReferenceOrientedTotalFlow();
        DecomposedFlow rescaledDecomposedFlow = new DecomposedFlow(decomposedFlow);
        rescaledDecomposedFlow.sum(noRelievingScaledDecomposedFlow.scale(scaleFactor));
        return rescaledDecomposedFlow;
    }

    Map<String, DecomposedFlow> rescale(Map<String, DecomposedFlow> decomposedFlowMap) {
        Map<String, DecomposedFlow> rescaledDecomposedFlowMap = new TreeMap<>();
        decomposedFlowMap.forEach((s, decomposedFlow) -> rescaledDecomposedFlowMap.put(s, rescale(decomposedFlow)));
        return rescaledDecomposedFlowMap;
    }
}
