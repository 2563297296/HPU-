package cn.edu.hpu.powerwidget;

import java.net.URL;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.Map;

final class HtmlForm {
    static final class Option {
        final String value;
        final String label;
        Option(String value, String label) { this.value=value; this.label=label; }
    }
    final URL action;
    final LinkedHashMap<String, String> fields;
    private final LinkedHashMap<String, Set<String>> values;
    private final Map<String, Map<String, String>> labels;

    HtmlForm(URL action, Map<String, String> fields, Map<String, Set<String>> values,
             Map<String, Map<String, String>> labels) {
        this.action = action;
        this.fields = new LinkedHashMap<>(fields);
        this.values = new LinkedHashMap<>();
        for (Map.Entry<String, Set<String>> entry : values.entrySet()) {
            this.values.put(entry.getKey(), Collections.unmodifiableSet(entry.getValue()));
        }
        this.labels = labels;
    }

    String get(String name) {
        return fields.get(name);
    }

    void put(String name, String value) {
        fields.put(name, value == null ? "" : value);
    }

    void remove(String name) {
        fields.remove(name);
    }

    boolean hasOption(String name, String value) {
        Set<String> allowed = values.get(name);
        return allowed != null && allowed.contains(value == null ? "" : value);
    }

    Set<String> options(String name) {
        Set<String> allowed = values.get(name);
        return allowed == null ? Collections.<String>emptySet() : allowed;
    }

    String optionLabel(String name, String value) {
        Map<String, String> map = labels.get(name);
        String label = map == null ? null : map.get(value);
        return label == null ? value : label;
    }
}
