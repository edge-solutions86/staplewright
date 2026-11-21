package io.github.rkaushikdev.staplewright.spec;

import io.github.rkaushikdev.staplewright.Facts;
import io.github.rkaushikdev.staplewright.KeywayError;
import io.github.rkaushikdev.staplewright.util.Text;
import java.util.Optional;

/**
 * One warding section, named the way the catalogue names it.
 *
 * @param code the section's code, upper case and with no white space around it
 * @param manufacturer who makes it
 * @param restricted whether blanks for it are sold to the shop only
 * @param patentExpires the day the patent on a restricted section runs out, as written
 */
public record KeywaySection(String code, String manufacturer, boolean restricted, Optional<String> patentExpires) {

    public KeywaySection {
        if (Text.isBlank(code)) {
            throw new KeywayError("a section needs a code");
        }
        if (!code.equals(Text.upper(Text.strip(code)))) {
            throw new KeywayError("section codes are upper case and untrimmed", Facts.of("code", code));
        }
    }

    /** An open section with no patent on it. */
    public KeywaySection(String code, String manufacturer) {
        this(code, manufacturer, false, Optional.empty());
    }

    /** A section that is restricted or not, with no patent date. */
    public KeywaySection(String code, String manufacturer, boolean restricted) {
        this(code, manufacturer, restricted, Optional.empty());
    }
}
