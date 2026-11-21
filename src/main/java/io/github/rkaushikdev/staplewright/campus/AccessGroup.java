package io.github.rkaushikdev.staplewright.campus;

import io.github.rkaushikdev.staplewright.InputError;
import io.github.rkaushikdev.staplewright.util.Text;

/**
 * A set of people who need the same doors.
 *
 * @param name what the department calls them
 * @param description a word about who they are
 * @param department the department that asked for their access
 */
public record AccessGroup(String name, String description, String department) {

    /**
     * An access group, refusing one with no name.
     *
     * @throws InputError for a blank name
     */
    public AccessGroup {
        if (Text.isBlank(name)) {
            throw new InputError("an access group needs a name");
        }
    }

    /** A group with nothing written about it and no department. */
    public AccessGroup(String name) {
        this(name, "", "");
    }
}
