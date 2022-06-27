/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.flow_decomposition;

import com.powsybl.commons.datasource.FileDataSource;
import com.powsybl.iidm.export.Exporters;
import com.powsybl.iidm.import_.Importers;
import com.powsybl.iidm.network.*;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Sebastien Murgey {@literal <sebastien.murgey at rte-france.com>}
 * @author Hugo Schindler {@literal <hugo.schindler at rte-france.com>}
 */

class AllocatedFlowTests {
    private static final double EPSILON = 1e-3;
    private static final double EPSILON_RELATIF = 2e-2;
    private static final double HUGE_EPSILON_RELATIF = 1e-1;

    private Network importNetwork(String networkResourcePath) {
        String networkName = Paths.get(networkResourcePath).getFileName().toString();
        return Importers.loadNetwork(networkName, getClass().getResourceAsStream(networkResourcePath));
    }

    @Test
    void checkThatAllocatedFlowAreExtractedForEachXnecGivenABasicNetwork() {
        String networkFileName = "NETWORK_SINGLE_LOAD_TWO_GENERATORS_WITH_COUNTRIES.uct";
        String gen_be = "BGEN2 11_generator";
        String load_be = "BLOAD 11_load";
        String gen_fr = "FGEN1 11_generator";
        String xnec_fr_be = "FGEN1 11 BLOAD 11 1";
        String allocated = "Allocated";

        Network network = importNetwork(networkFileName);
        FlowDecompositionComputer allocatedFlowComputer = new FlowDecompositionComputer();
        FlowDecompositionResults flowDecompositionResults = allocatedFlowComputer.run(network, true);

        Map<String, DecomposedFlow> decomposedFlowMap = flowDecompositionResults.getDecomposedFlowsMap();
        assertEquals(100, decomposedFlowMap.get(xnec_fr_be).getAllocatedFlow(), EPSILON);

        Map<Country, Map<String, Double>> glsks = flowDecompositionResults.getGlsks();
        assertEquals(1.0, glsks.get(Country.FR).get(gen_fr), EPSILON);
        assertEquals(1.0, glsks.get(Country.BE).get(gen_be), EPSILON);

        Map<String, Map<String, Double>> ptdfs = flowDecompositionResults.getPtdfMap();
        assertEquals(-0.5, ptdfs.get(xnec_fr_be).get(load_be));
        assertEquals(-0.5, ptdfs.get(xnec_fr_be).get(gen_be));
        assertEquals(+0.5, ptdfs.get(xnec_fr_be).get(gen_fr));

        Map<String, Map<String, Double>> nodalInjection = flowDecompositionResults.getNodalInjectionsMap();
        assertEquals(-100, nodalInjection.get(gen_be).get(allocated));
        assertEquals(+100, nodalInjection.get(gen_fr).get(allocated));
    }

    @Test
    void checkThatAllocatedFlowAreExtractedForEachXnecGivenACoreNetwork() {
        String networkFileName = "20220611_2130_2D6_UX2_FEXPORTGRIDMODEL_CGM_17XTSO-CS------W.uct";

        Network network = importNetwork(networkFileName);
        FlowDecompositionComputer allocatedFlowComputer = new FlowDecompositionComputer();
        FlowDecompositionResults flowDecompositionResults = allocatedFlowComputer.run(network, true);
        assertNotNull(flowDecompositionResults);
    }

    @Test
    void checkThatAllocatedFlowAreExtractedForEachXnecGivenACERNetwork() {
        String networkFileName = "20211117_1030_2D3_UX0.uct";

        Network network = importNetwork(networkFileName);
        network.getBranch("A2    11 V1    11 1");
        FlowDecompositionComputer allocatedFlowComputer = new FlowDecompositionComputer();
        FlowDecompositionResults flowDecompositionResults = allocatedFlowComputer.run(network, true);

        Map<String, DecomposedFlow> decomposedFlowMap = flowDecompositionResults.getDecomposedFlowsMap();
        assertNotNull(flowDecompositionResults);
    }

    private void assertRelatifEquals(double expected, double actual, double delta) {
        //System.out.println(Math.abs(expected-actual)/actual);
        assertEquals(0, Math.abs(expected-actual)/actual, delta);
    }

    @Test
    void checkThatAllocatedFlowAreExtractedForEachXnecGivenALoadCompensedHLBPNetwork() {
        String networkFileName = "NETWORK_HLBP.xiidm";
        String A1 = "A1    11_generator";
        String B1 = "B1    11_generator";
        String B3 = "B3    11_generator";
        String C4 = "C4    11_generator";
        String D3 = "D3    11_generator";
        String N1 = "N1    11_generator";
        String V1 = "V1    11_generator";
        String W2 = "W2    11_generator";
        String allocated = "Allocated";
        String twt_id = "C1    11 C3    11 1";
        String A1B1 = "A1    11 X1    11 1 + X1    11 B1    11 1";
        String A2V1 = "A2    11 V1    11 1";
        String B1C1 = "B1    11 X2    11 1 + X2    11 C1    11 1";
        String B4D1 = "B4    11 X3    11 1 + X3    11 D1    11 1";
        String B5N1 = "B5    11 X6    11 1 + X6    11 N1    11 1";
        String C3D2 = "C3    11 X4    11 1 + X4    11 D2    11 1";
        String C4D2 = "C4    11 X5    11 1 + X5    11 D2    11 1";
        String D3N2 = "D3    11 X7    11 1 + X7    11 N2    11 1";
        String W2C2 = "W2    11 C2    11 1";

        Network network = importNetwork(networkFileName);
        LoadFlowParameters parameters = LoadFlowParameters.load();
        parameters.setDc(false);
        parameters.setDistributedSlack(true);
        parameters.setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P);
        LoadFlow.run(network, parameters);
        Exporters.export("XIIDM", network, new Properties(), new FileDataSource(Path.of("/tmp"), "000-init"));
        LossesCompensationEngine engine = new LossesCompensationEngine();
        engine.compensateLosses(network);
        Exporters.export("XIIDM", network, new Properties(), new FileDataSource(Path.of("/tmp"), "001-afterCompensation"));
        TwoWindingsTransformer twt = network.getTwoWindingsTransformer(twt_id);
        System.out.println(twt.getPhaseTapChanger().getAllSteps().get(0).getAlpha() - twt.getPhaseTapChanger().getAllSteps().get(1).getAlpha());
        FlowDecompositionComputer allocatedFlowComputer = new FlowDecompositionComputer();
        FlowDecompositionResults flowDecompositionResults = allocatedFlowComputer.run(network, true);
        Exporters.export("XIIDM", network, new Properties(), new FileDataSource(Path.of("/tmp"), "002-afterAnalysis"));

        Map<Country, Map<String, Double>> glsks = flowDecompositionResults.getGlsks();
        assertEquals(1.0, glsks.get(Country.AL).get(A1));
        assertEquals(0.6, glsks.get(Country.BE).get(B1));
        assertEquals(0.4, glsks.get(Country.BE).get(B3));
        assertEquals(1.0, glsks.get(Country.CZ).get(C4));
        assertEquals(1.0, glsks.get(Country.DE).get(D3));
        assertEquals(1.0, glsks.get(Country.NL).get(N1));
        assertEquals(1.0, glsks.get(Country.BG).get(V1));
        assertEquals(1.0, glsks.get(Country.BA).get(W2));

        Map<String, Map<String, Double>> ptdfs = flowDecompositionResults.getPtdfMap();

        Map<String, Map<String, Double>> nodalInjection = flowDecompositionResults.getNodalInjectionsMap();
        assertRelatifEquals(598.6  , nodalInjection.get(A1).get(allocated), EPSILON_RELATIF);
        assertRelatifEquals(542.2  , nodalInjection.get(B1).get(allocated), EPSILON_RELATIF);
        assertRelatifEquals(361.5  , nodalInjection.get(B3).get(allocated), EPSILON_RELATIF);
        assertRelatifEquals(-500.1 , nodalInjection.get(C4).get(allocated), EPSILON_RELATIF);
        assertRelatifEquals(-1103.1, nodalInjection.get(D3).get(allocated), EPSILON_RELATIF);
        assertRelatifEquals(100.9  , nodalInjection.get(N1).get(allocated), EPSILON_RELATIF);
        assertRelatifEquals(-600.0 , nodalInjection.get(V1).get(allocated), EPSILON_RELATIF);
        assertRelatifEquals(600.0  , nodalInjection.get(W2).get(allocated), EPSILON_RELATIF);

        Map<String, DecomposedFlow> decomposedFlowMap = flowDecompositionResults.getDecomposedFlowsMap();
        //assertRelatifEquals(-1.4    , decomposedFlowMap.get(A1B1).getAllocatedFlow(), HUGE_EPSILON_RELATIF);
        //assertRelatifEquals(0.0    , decomposedFlowMap.get(A2V1).getAllocatedFlow(), HUGE_EPSILON_RELATIF); // HVDC
        assertRelatifEquals(149.1  , decomposedFlowMap.get(B1C1).getAllocatedFlow(), HUGE_EPSILON_RELATIF);
        assertRelatifEquals(491.3  , decomposedFlowMap.get(B4D1).getAllocatedFlow(), HUGE_EPSILON_RELATIF);
        assertRelatifEquals(261.9  , decomposedFlowMap.get(B5N1).getAllocatedFlow(), HUGE_EPSILON_RELATIF);
        assertRelatifEquals(286.8  , decomposedFlowMap.get(C3D2).getAllocatedFlow(), HUGE_EPSILON_RELATIF);
        assertRelatifEquals(-37.8  , decomposedFlowMap.get(C4D2).getAllocatedFlow(), HUGE_EPSILON_RELATIF);
        assertRelatifEquals(-362.8  , decomposedFlowMap.get(D3N2).getAllocatedFlow(), HUGE_EPSILON_RELATIF);
        //assertRelatifEquals(0.0    , decomposedFlowMap.get(W2C2).getAllocatedFlow(), HUGE_EPSILON_RELATIF); // HVDC
        //System.out.println(decomposedFlowMap.get(twt_id).getAllocatedFlow());
    }
}
