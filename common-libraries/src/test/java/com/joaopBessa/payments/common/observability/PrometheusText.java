package com.joaopBessa.payments.common.observability;

import java.util.Arrays;
import java.util.List;

public final class PrometheusText {

    private static final String EXEMPLAR_SEPARATOR = " # ";

    private final List<String> lines;

    public PrometheusText(String body) {
        this.lines = body.lines().filter(line -> !line.startsWith("#")).toList();
    }

    public boolean hasSeries(String metric, String... labelFragments) {
        return matching(metric, labelFragments).findAny().isPresent();
    }

    public double sum(String metric, String... labelFragments) {
        return matching(metric, labelFragments)
                .mapToDouble(PrometheusText::sampleValue)
                .sum();
    }

    private static double sampleValue(String line) {
        String sample = line.contains(EXEMPLAR_SEPARATOR) ? line.substring(0, line.indexOf(EXEMPLAR_SEPARATOR)) : line;
        return Double.parseDouble(sample.substring(sample.lastIndexOf(' ') + 1).trim());
    }

    private java.util.stream.Stream<String> matching(String metric, String... labelFragments) {
        return lines.stream()
                .filter(line -> line.startsWith(metric + "{") || line.startsWith(metric + " "))
                .filter(line -> Arrays.stream(labelFragments).allMatch(line::contains));
    }

}
