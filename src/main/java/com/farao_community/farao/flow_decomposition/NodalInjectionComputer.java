/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.flow_decomposition;

import com.powsybl.commons.PowsyblException;
import com.powsybl.iidm.network.Country;
import com.powsybl.iidm.network.Identifiable;
import com.powsybl.iidm.network.Injection;
import com.powsybl.iidm.network.Network;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author Hugo Schindler {@literal <hugo.schindler at rte-france.com>}
 */
class NodalInjectionComputer {
    private static final double DEFAULT_GLSK_FACTOR = 0.0;
    private static final String ALLOCATED_COLUMN_NAME = "Allocated Flow";
    private final NetworkIndexes networkIndexes;

    public NodalInjectionComputer(NetworkIndexes networkIndexes) {
        this.networkIndexes = networkIndexes;
    }

    SparseMatrixWithIndexesTriplet getNodalInjectionsMatrix(
        Network network,
        Map<Country, Map<String, Double>> glsks,
        Map<Country, Double> netPositions,
        Map<String, Double> dcNodalInjection) {
        Map<String, Double> nodalInjectionsForAllocatedFlow = getNodalInjectionsForAllocatedFlows(glsks, netPositions);
        return convertToNodalInjectionMatrix(network, glsks, nodalInjectionsForAllocatedFlow, dcNodalInjection);
    }

    private Map<String, Double> getNodalInjectionsForAllocatedFlows(
        Map<Country, Map<String, Double>> glsks,
        Map<Country, Double> netPositions) {
        return networkIndexes.getNodeList().stream()
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
        Map<String, Double> dcNodalInjection) {
        List<String> columns = glsks.keySet().stream()
            .map(NetworkUtil::getLoopFlowIdFromCountry)
            .collect(Collectors.toList());
        columns.add(ALLOCATED_COLUMN_NAME);
        SparseMatrixWithIndexesTriplet nodalInjectionMatrix = new SparseMatrixWithIndexesTriplet(
            networkIndexes.getNodeIndex(), NetworkUtil.getIndex(columns), nodalInjectionsForAllocatedFlow.size());
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
        return networkIndexes.getNodeList().stream()
            .collect(Collectors.toMap(
                Identifiable::getId,
                this::getReferenceInjection
            ));
    }

    private double getReferenceInjection(Injection<?> node) {
        double p = -node.getTerminal().getP();
        if (Double.isNaN(p)) {
            throw new PowsyblException(String.format("Reference nodal injection cannot be a Nan for node %s", node.getId()));
        }
        return p;
    }
}
