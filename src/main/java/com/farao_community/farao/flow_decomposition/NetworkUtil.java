/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.flow_decomposition;

import com.powsybl.commons.PowsyblException;
import com.powsybl.iidm.network.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * @author Hugo Schindler{@literal <hugo.schindler@rte-france.com>}
 * @author Sebastien Murgey{@literal <sebastien.murgey at rte-france.com>}
 */
final class NetworkUtil {
    static final String LOOP_FLOWS_COLUMN_PREFIX = "Loop Flow from";

    private NetworkUtil() {
        throw new AssertionError("Utility class should not be instantiated");
    }

    static String getLoopFlowIdFromCountry(Country country) {
        return String.format("%s %s", LOOP_FLOWS_COLUMN_PREFIX, country.toString());
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

    static String getLoopFlowIdFromCountry(Network network, String identifiableId) {
        Identifiable<?> identifiable = network.getIdentifiable(identifiableId);
        if (identifiable instanceof Injection) {
            return getLoopFlowIdFromCountry(getInjectionCountry((Injection<?>) identifiable));
        }
        throw new PowsyblException(String.format("Identifiable %s must be an Injection", identifiableId));
    }

    static Map<String, Integer> getIndex(List<String> idList) {
        return IntStream.range(0, idList.size())
            .boxed()
            .collect(Collectors.toMap(
                idList::get,
                Function.identity()
            ));
    }

    static boolean isTerminalInMainSynchronousComponent(Terminal terminal) {
        return terminal.getBusBreakerView().getBus().isInMainSynchronousComponent();
    }
}
