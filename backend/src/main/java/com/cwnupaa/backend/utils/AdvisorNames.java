package com.cwnupaa.backend.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Normalizes signup arrays and legacy List.toString() values at the data boundary. */
public final class AdvisorNames {
    private static final ObjectMapper JSON = new ObjectMapper();
    private AdvisorNames() {}

    public static String normalize(Object value) {
        if (value == null) return null;
        List<String> names = new ArrayList<>();
        append(value, names);
        return String.join("、", names);
    }

    private static void append(Object value, List<String> names) {
        if (value == null) return;
        if (value instanceof Collection<?> values) {
            values.forEach(item -> append(item, names));
            return;
        }
        String text = TextUtils.trim(String.valueOf(value));
        if (text.startsWith("[") && text.endsWith("]")) {
            try {
                JsonNode parsed = JSON.readTree(text);
                if (parsed.isArray()) {
                    parsed.forEach(item -> { if (!item.isNull()) append(item.isTextual() ? item.asText() : item.toString(), names); });
                    return;
                }
            } catch (java.io.IOException ignored) { /* Legacy Java lists are not JSON. */ }
            text = text.substring(1, text.length() - 1).strip();
        }
        for (String part : text.split("[、,，;；\\r\\n]+")) {
            String name = TextUtils.trim(part);
            if (name.length() >= 2 && ((name.startsWith("\"") && name.endsWith("\"")) || (name.startsWith("'") && name.endsWith("'")))) {
                name = TextUtils.trim(name.substring(1, name.length() - 1));
            }
            if (!name.isEmpty()) names.add(name);
        }
    }
}
