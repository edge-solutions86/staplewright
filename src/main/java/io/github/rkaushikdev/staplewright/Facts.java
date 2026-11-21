package io.github.rkaushikdev.staplewright;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The facts a refusal carries, written as name and value pairs.
 *
 * <p>A fact may be {@code null}, which is how a refusal says a thing was not set. Names keep the
 * order they were written in, which is the order a service body lists them.
 */
public final class Facts {

    private Facts() {}

    /**
     * Facts from alternating names and values: {@code Facts.of("chamber", 4, "depth", 9)}.
     *
     * @throws InputError when the pairs do not pair up or a name is not text
     */
    public static Map<String, Object> of(Object... pairs) {
        if (pairs.length % 2 != 0) {
            throw new InputError("facts come in name and value pairs");
        }
        Map<String, Object> out = new LinkedHashMap<>();
        for (int index = 0; index < pairs.length; index += 2) {
            if (!(pairs[index] instanceof String name)) {
                throw new InputError("a fact is named with text");
            }
            out.put(name, pairs[index + 1]);
        }
        return Collections.unmodifiableMap(out);
    }

    /** The first facts followed by the second, the second winning on a shared name. */
    public static Map<String, Object> merged(Map<String, ?> first, Map<String, ?> second) {
        Map<String, Object> out = new LinkedHashMap<>(first);
        out.putAll(second);
        return Collections.unmodifiableMap(out);
    }
}
