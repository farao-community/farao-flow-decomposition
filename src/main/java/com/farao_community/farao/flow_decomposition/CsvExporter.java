/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.flow_decomposition;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * @author Sebastien Murgey {@literal <sebastien.murgey at rte-france.com>}
 * @author Hugo Schindler {@literal <hugo.schindler at rte-france.com>}
 */
public class CsvExporter {
    public static final Path DEFAULT_EXPORT_DIR = Path.of("/tmp");
    public static final Charset CHARSET = StandardCharsets.UTF_8;
    public static final CSVFormat FORMAT = CSVFormat.RFC4180;
    private static final Logger LOGGER = LoggerFactory.getLogger(CsvExporter.class);

    public void export(FlowDecompositionParameters parameters, FlowDecompositionResults flowDecompositionResults) {
        export(DEFAULT_EXPORT_DIR, flowDecompositionResults.getId(), flowDecompositionResults.getDecomposedFlowsMap());
    }

    public void export(Path dirPath, FlowDecompositionParameters parameters, FlowDecompositionResults flowDecompositionResults) {
        export(dirPath, flowDecompositionResults.getId(), flowDecompositionResults.getDecomposedFlowsMap());
    }

    void export(Path dirPath, String basename, Map<String, DecomposedFlow> decomposedFlowMap) {
        Path path = Paths.get(dirPath.toString(), basename + ".csv");
        LOGGER.debug("Saving flow decomposition decomposedFlowMap in file {}", path);
        try (
            BufferedWriter writer = Files.newBufferedWriter(path, CHARSET);
            CSVPrinter printer = new CSVPrinter(writer, FORMAT);
        ) {
            printHeaderRow(decomposedFlowMap, printer);
            printContentRows(decomposedFlowMap, printer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void printHeaderRow(Map<String, DecomposedFlow> decomposedFlowMap, CSVPrinter printer) throws IOException {
        printer.print("");
        for (Map.Entry<String, DecomposedFlow> xnecId : decomposedFlowMap.entrySet()) {
            printer.print(xnecId.getKey());
        }
        printer.println();
    }

    private void printContentRows(Map<String, DecomposedFlow> decomposedFlowMap, CSVPrinter printer) throws IOException {
        Collection<DecomposedFlow> decomposedFlows = decomposedFlowMap.values();
        Set<String> columnKeys = getInnerKeySet(decomposedFlowMap);
        for (String columnKey : columnKeys) {
            printer.print(columnKey);
            for (DecomposedFlow decomposedFlow : decomposedFlows) {
                printer.print(decomposedFlow.get(columnKey));
            }
            printer.println();
        }
    }

    private Set<String> getInnerKeySet(Map<String, DecomposedFlow> decomposedFlowMap) {
        return decomposedFlowMap.entrySet().iterator().next().getValue().keySet();
    }
}
