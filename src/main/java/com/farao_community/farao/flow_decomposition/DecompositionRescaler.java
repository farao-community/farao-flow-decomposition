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
        DecomposedFlow noRelievingScaledDecomposedFlow = replaceRelievingFlows(decomposedFlow);
        double scaleFactor = (decomposedFlow.getAcReferenceFlow() - decomposedFlow.getReferenceOrientedTotalFlow())
            / noRelievingScaledDecomposedFlow.getReferenceOrientedTotalFlow();
        return sum(decomposedFlow, scale(noRelievingScaledDecomposedFlow, scaleFactor));
    }

    Map<String, DecomposedFlow> rescale(Map<String, DecomposedFlow> decomposedFlowMap) {
        Map<String, DecomposedFlow> rescaledDecomposedFlowMap = new TreeMap<>();
        decomposedFlowMap.forEach((s, decomposedFlow) -> rescaledDecomposedFlowMap.put(s, rescale(decomposedFlow)));
        return rescaledDecomposedFlowMap;
    }

    DecomposedFlow replaceRelievingFlows(DecomposedFlow decomposedFlow) {
        DecomposedFlow newDecomposedFlow = new DecomposedFlow(decomposedFlow);
        decomposedFlow.keySet()
            .forEach(key -> newDecomposedFlow.put(key, reLU(decomposedFlow.get(key))));
        return newDecomposedFlow;
    }

    private double reLU(double value) {
        return value > 0 ? value : 0.;
    }

    DecomposedFlow scale(DecomposedFlow decomposedFlow, double coefficient) {
        DecomposedFlow newDecomposedFlow = new DecomposedFlow(decomposedFlow);
        decomposedFlow.keySet()
            .forEach(key -> newDecomposedFlow.put(key, decomposedFlow.get(key) * coefficient));
        return newDecomposedFlow;
    }

    DecomposedFlow sum(DecomposedFlow decomposedFlow, DecomposedFlow otherDecomposedFlow) {
        DecomposedFlow newDecomposedFlow = new DecomposedFlow(decomposedFlow);
        decomposedFlow.keySet()
            .forEach(key -> newDecomposedFlow.put(key, decomposedFlow.get(key) + otherDecomposedFlow.get(key)));
        return newDecomposedFlow;
    }
}
