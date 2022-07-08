package com.farao_community.farao.flow_decomposition;

public class DecomposedFlowRescaler {
    public DecomposedFlow rescale(DecomposedFlow decomposedFlow) {
        DecomposedFlow rescaledDecomposedFlow = new DecomposedFlow(decomposedFlow);
        rescaledDecomposedFlow.replaceRelievingFlows();
        Double totalFlow = rescaledDecomposedFlow.getTotalFlow();
        rescaledDecomposedFlow.scale(decomposedFlow.getAcReferenceFlow()/totalFlow);
        return rescaledDecomposedFlow;
    }
}
