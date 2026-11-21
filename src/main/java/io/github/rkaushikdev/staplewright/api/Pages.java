package io.github.rkaushikdev.staplewright.api;

import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * A report over the wire: a list of lines for a program, or the page as text for a person.
 *
 * <p>Every report route takes the same {@code plain} query flag, described the same way, so a
 * caller never has to remember which route spells it differently.
 */
public final class Pages {

    /** What the {@code plain} flag means, on every route that takes it. */
    public static final String PLAIN = "give it back as plain text";

    private Pages() {}

    /** The lines as {@code {"lines": [...]}}, or as text, one line each, when {@code plain}. */
    public static ResponseEntity<?> page(List<String> lines, boolean plain) {
        if (plain) {
            return ResponseEntity.ok()
                    .contentType(new MediaType(MediaType.TEXT_PLAIN, java.nio.charset.StandardCharsets.UTF_8))
                    .body(String.join("\n", lines));
        }
        return ResponseEntity.ok(Map.of("lines", List.copyOf(lines)));
    }
}
