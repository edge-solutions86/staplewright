package io.github.rkaushikdev.staplewright.campus;

import java.util.List;

/**
 * A group whose nearest key opens more than it asked for.
 *
 * @param group the access group
 * @param symbol the key it would be issued, as the shop writes it
 * @param extra the doors that key opens and the group never asked for, in number order
 */
public record OverGrant(String group, String symbol, List<String> extra) {

    public OverGrant {
        extra = List.copyOf(extra);
    }
}
