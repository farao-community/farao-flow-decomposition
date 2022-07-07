/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.flow_decomposition;

import com.powsybl.contingency.ContingencyContext;
import com.powsybl.iidm.network.Branch;
import com.powsybl.iidm.network.Network;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.sensitivity.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author Hugo Schindler {@literal <hugo.schindler at rte-france.com>}
 * @author Sebastien Murgey {@literal <sebastien.murgey at rte-france.com>}
 */
class SensitivityAnalyser {
    private static final int VARIABLE_BATCH_SIZE = 15000;
    private static final double DEFAULT_SENSIBILITY_EPSILON = 1e-5;
    private static final Logger LOGGER = LoggerFactory.getLogger(SensitivityAnalyser.class);
    private final SensitivityAnalysisParameters sensitivityAnalysisParameters;
    private final Network network;
    private final List<Branch> functionList;
    private final Map<String, Integer> functionIndex;

    SensitivityAnalyser(LoadFlowParameters loadFlowParameters,
                        Network network,
                        List<Branch> functionList,
                        Map<String, Integer> functionIndex) {
        this.sensitivityAnalysisParameters = initSensitivityAnalysisParameters(loadFlowParameters);
        this.network = network;
        this.functionList = functionList;
        this.functionIndex = functionIndex;
    }

    public SensitivityAnalyser(LoadFlowParameters loadFlowParameters, Network network, NetworkMatrixIndexes networkMatrixIndexes) {
        this(loadFlowParameters, network, networkMatrixIndexes.getXnecList(), networkMatrixIndexes.getXnecIndex());
    }

    private static SensitivityAnalysisParameters initSensitivityAnalysisParameters(LoadFlowParameters loadFlowParameters) {
        SensitivityAnalysisParameters parameters = SensitivityAnalysisParameters.load();
        parameters.setLoadFlowParameters(loadFlowParameters);
        LOGGER.debug("Using following sensitivity analysis parameters: {}", parameters);
        return parameters;
    }

    SparseMatrixWithIndexesTriplet run(List<String> variableList,
                                       Map<String, Integer> variableIndex,
                                       SensitivityVariableType sensitivityVariableType) {
        SparseMatrixWithIndexesTriplet sensiMatrixTriplet = initSensiMatrixTriplet(variableIndex);
        for (int i = 0; i < variableList.size(); i += VARIABLE_BATCH_SIZE) {
            List<String> localNodeList = variableList.subList(i, Math.min(variableList.size(), i + VARIABLE_BATCH_SIZE));
            partialFillSensitityMatrix(sensitivityVariableType, sensiMatrixTriplet, localNodeList);
        }
        return sensiMatrixTriplet;
    }

    private SparseMatrixWithIndexesTriplet initSensiMatrixTriplet(Map<String, Integer> variableIndex) {
        LOGGER.debug("Filtering Sensitivity values with epsilon = {}", DEFAULT_SENSIBILITY_EPSILON);
        return new SparseMatrixWithIndexesTriplet(functionIndex,
            variableIndex,
            functionIndex.size() * variableIndex.size(),
            DEFAULT_SENSIBILITY_EPSILON);
    }

    private void partialFillSensitityMatrix(SensitivityVariableType sensitivityVariableType,
                                            SparseMatrixWithIndexesTriplet sensiMatrixTriplet,
                                            List<String> localNodeList) {
        List<SensitivityFactor> factors = getFactors(localNodeList, sensitivityVariableType);
        SensitivityAnalysisResult sensiResult = getSensitivityAnalysisResult(factors);
        fillSensibilityMatrixTriplet(sensiMatrixTriplet, factors, sensiResult);
    }

    private List<SensitivityFactor> getFactors(List<String> variableList,
                                               SensitivityVariableType sensitivityVariableType) {
        List<SensitivityFactor> factors = new ArrayList<>();
        variableList.forEach(
            variable -> functionList.forEach(
                function -> factors.add(getSensitivityFactor(variable, function, sensitivityVariableType))));
        return factors;
    }

    private SensitivityFactor getSensitivityFactor(String variable,
                                                   Branch<?> function,
                                                   SensitivityVariableType sensitivityVariableType) {
        return new SensitivityFactor(
            SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, function.getId(),
            sensitivityVariableType, variable,
            false,
            ContingencyContext.none()
        );
    }

    private SensitivityAnalysisResult getSensitivityAnalysisResult(List<SensitivityFactor> factors) {
        return SensitivityAnalysis.run(network, factors, sensitivityAnalysisParameters);
    }

    private void fillSensibilityMatrixTriplet(
        SparseMatrixWithIndexesTriplet ptdfMatrixTriplet,
        List<SensitivityFactor> factors,
        SensitivityAnalysisResult sensiResult) {
        for (SensitivityValue sensitivityValue : sensiResult.getValues()) {
            SensitivityFactor factor = factors.get(sensitivityValue.getFactorIndex());
            double sensitivity = sensitivityValue.getValue();
            double referenceOrientedSensitivity = sensitivityValue.getFunctionReference() < 0 ?
                -sensitivity : sensitivity;
            ptdfMatrixTriplet.addItem(factor.getFunctionId(), factor.getVariableId(), referenceOrientedSensitivity);
        }
    }
}
