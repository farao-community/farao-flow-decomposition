package com.farao_community.farao.flow_decomposition;

import com.powsybl.commons.PowsyblException;
import com.powsybl.iidm.network.Country;
import com.powsybl.iidm.network.Identifiable;
import com.powsybl.iidm.network.Injection;
import com.powsybl.iidm.network.Network;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

class NodalInjectionComputer {
    private static final double DEFAULT_GLSK_FACTOR = 0.0;
    private static final String ALLOCATED_COLUMN_NAME = "Allocated Flow";
    private final List<Injection<?>> nodeList;

    public NodalInjectionComputer(List<Injection<?>> nodeList) {
        this.nodeList = nodeList;
    }

    SparseMatrixWithIndexesTriplet getNodalInjectionsMatrix(
        Network network,
        Map<Country, Map<String, Double>> glsks,
        Map<Country, Double> netPositions,
        Map<String, Double> dcNodalInjection,
        Map<String, Integer> nodeIndex) {
        Map<String, Double> nodalInjectionsForAllocatedFlow = getNodalInjectionsForAllocatedFlows(glsks, netPositions);
        return convertToNodalInjectionMatrix(network, glsks, nodalInjectionsForAllocatedFlow, dcNodalInjection, nodeIndex);
    }

    private Map<String, Double> getNodalInjectionsForAllocatedFlows(
        Map<Country, Map<String, Double>> glsks,
        Map<Country, Double> netPositions) {
        return nodeList.stream()
            .collect(Collectors.toMap(
                    Injection::getId,
                    injection -> getIndividualNodalInjectionForAllocatedFlows(injection, glsks, netPositions)
                )
            );
    }

    private double getIndividualNodalInjectionForAllocatedFlows(
        Injection<?> injection,
        Map<Country, Map<String, Double>> glsks,
        Map<Country, Double> netPositions) {
        Country injectionCountry = NetworkUtil.getInjectionCountry(injection);
        return glsks.get(injectionCountry).getOrDefault(injection.getId(), DEFAULT_GLSK_FACTOR)
            * netPositions.get(injectionCountry);
    }

    private SparseMatrixWithIndexesTriplet convertToNodalInjectionMatrix(
        Network network,
        Map<Country, Map<String, Double>> glsks,
        Map<String, Double> nodalInjectionsForAllocatedFlow,
        Map<String, Double> dcNodalInjection,
        Map<String, Integer> nodeIndex) {
        List<String> columns = glsks.keySet().stream()
            .map(NetworkUtil::getLoopFlowIdFromCountry)
            .collect(Collectors.toList());
        columns.add(ALLOCATED_COLUMN_NAME);
        SparseMatrixWithIndexesTriplet nodalInjectionMatrix = new SparseMatrixWithIndexesTriplet(
            nodeIndex, NetworkUtil.getIndex(columns), nodalInjectionsForAllocatedFlow.size());
        nodalInjectionsForAllocatedFlow.forEach(
            (injectionId, injectionValue) -> nodalInjectionMatrix.addItem(injectionId, ALLOCATED_COLUMN_NAME, injectionValue)
        );
        dcNodalInjection.forEach(
            (dcInjectionId, dcInjectionValue) -> nodalInjectionMatrix.addItem(
                dcInjectionId,
                NetworkUtil.getLoopFlowIdFromCountry(NetworkUtil.getIdentifiableCountry(network, dcInjectionId)),
                dcInjectionValue - nodalInjectionsForAllocatedFlow.get(dcInjectionId)
            ));
        return nodalInjectionMatrix;
    }

    Map<String, Double> getDCNodalInjections() {
        return nodeList.stream()
            .collect(Collectors.toMap(
                Identifiable::getId,
                this::getReferenceInjection
            ));
    }

    private double getReferenceInjection(Injection<?> node) {
        double p = -((Injection<?>) node).getTerminal().getP();
        if (Double.isNaN(p)) {
            throw new PowsyblException(String.format("Reference nodal injection cannot be a Nan for node %s", node.getId()));
        }
        return p;
    }

}
