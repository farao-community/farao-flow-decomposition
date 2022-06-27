/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.flow_decomposition;

import com.powsybl.commons.PowsyblException;
import com.powsybl.iidm.network.*;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * @author Sebastien Murgey{@literal <sebastien.murgey at rte-france.com>}
 */
final class NetworkUtil {
    private NetworkUtil() {
        throw new AssertionError("Utility class should not be instantiated");
    }

    static Country getTerminalCountry(Terminal terminal) {
        Optional<Substation> optionalSubstation = terminal.getVoltageLevel().getSubstation();
        if (optionalSubstation.isEmpty()) {
            throw new PowsyblException(String.format("Voltage level %s does not belong to any substation. " +
                    "Cannot retrieve country info needed for the algorithm.", terminal.getVoltageLevel().getId()));
        }
        Substation substation = optionalSubstation.get();
        Optional<Country> optionalCountry = substation.getCountry();
        if (optionalCountry.isEmpty()) {
            throw new PowsyblException(String.format("Substation %s does not have country property" +
                    "needed for the algorithm.", substation.getId()));
        }
        return optionalCountry.get();
    }

    static Country getInjectionCountry(Injection<?> injection) {
        return getTerminalCountry(injection.getTerminal());
    }
    static Map<Country, Double> computeNetPositions(Network network) {
        Map<Country, Double> netPositions = new EnumMap<>(Country.class);

        network.getDanglingLineStream().forEach(danglingLine -> {
            Country country = NetworkUtil.getTerminalCountry(danglingLine.getTerminal());
            addLeavingFlow(netPositions, danglingLine, country);
        });

        network.getLineStream().forEach(line -> {
            Country countrySide1 = NetworkUtil.getTerminalCountry(line.getTerminal1());
            Country countrySide2 = NetworkUtil.getTerminalCountry(line.getTerminal2());
            if (countrySide1.equals(countrySide2)) {
                return;
            }
            addLeavingFlow(netPositions, line, countrySide1);
            addLeavingFlow(netPositions, line, countrySide2);
        });

        network.getHvdcLineStream().forEach(hvdcLine -> {
            Country countrySide1 = NetworkUtil.getTerminalCountry(hvdcLine.getConverterStation1().getTerminal());
            Country countrySide2 = NetworkUtil.getTerminalCountry(hvdcLine.getConverterStation2().getTerminal());
            if (countrySide1.equals(countrySide2)) {
                return;
            }
            addLeavingFlow(netPositions, hvdcLine, countrySide1);
            addLeavingFlow(netPositions, hvdcLine, countrySide2);
        });

        return netPositions;
    }

    private static void addLeavingFlow(Map<Country, Double> netPositions, DanglingLine danglingLine, Country country) {
        Double previousValue = getPreviousValue(netPositions, country);
        netPositions.put(country, previousValue + getLeavingFlow(danglingLine));
    }

    private static Double getPreviousValue(Map<Country, Double> netPositions, Country country) {
        Double previousValue;
        if (netPositions.get(country) != null) {
            previousValue = netPositions.get(country);
        } else {
            previousValue = (double) 0;
        }
        return previousValue;
    }

    private static void addLeavingFlow(Map<Country, Double> netPositions, Line line, Country country) {
        Double previousValue = getPreviousValue(netPositions, country);
        netPositions.put(country, previousValue + getLeavingFlow(line, country));
    }

    private static void addLeavingFlow(Map<Country, Double> netPositions, HvdcLine hvdcLine, Country country) {
        Double previousValue = getPreviousValue(netPositions, country);
        netPositions.put(country, previousValue + getLeavingFlow(hvdcLine, country));
    }

    private static double getLeavingFlow(DanglingLine danglingLine) {
        return danglingLine.getTerminal().isConnected() && !Double.isNaN(danglingLine.getTerminal().getP()) ? danglingLine.getTerminal().getP() : 0;
    }

    private static double getLeavingFlow(Line line, Country country) {
        double flowSide1 = line.getTerminal1().isConnected() && !Double.isNaN(line.getTerminal1().getP()) ? line.getTerminal1().getP() : 0;
        double flowSide2 = line.getTerminal2().isConnected() && !Double.isNaN(line.getTerminal2().getP()) ? line.getTerminal2().getP() : 0;
        double directFlow = (flowSide1 - flowSide2) / 2;
        return country.equals(NetworkUtil.getTerminalCountry(line.getTerminal1())) ? directFlow : -directFlow;
    }

    private static double getLeavingFlow(HvdcLine hvdcLine, Country country) {
        double flowSide1 = hvdcLine.getConverterStation1().getTerminal().isConnected() && !Double.isNaN(hvdcLine.getConverterStation1().getTerminal().getP()) ? hvdcLine.getConverterStation1().getTerminal().getP() : 0;
        double flowSide2 = hvdcLine.getConverterStation2().getTerminal().isConnected() && !Double.isNaN(hvdcLine.getConverterStation2().getTerminal().getP()) ? hvdcLine.getConverterStation2().getTerminal().getP() : 0;
        double directFlow = (flowSide1 - flowSide2) / 2;
        return country.equals(NetworkUtil.getTerminalCountry(hvdcLine.getConverterStation1().getTerminal())) ? directFlow : -directFlow;
    }
}
