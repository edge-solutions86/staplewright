package io.github.rkaushikdev.staplewright.control;

import java.util.List;

/**
 * Somebody who may sign for keys, and how far their signature reaches.
 *
 * <p>A signature covers one department. It covers every building that department uses unless the
 * authorizer is limited to a list of buildings, and it covers a master only when the authorizer is
 * allowed to approve masters, which most are not.
 *
 * @param identifier the keyholder doing the signing, who has to be in the directory
 * @param department the department the signature covers
 * @param buildings the buildings it reaches, or none at all for every building
 * @param mayApproveMasters whether this signature may approve a master
 */
public record Authorizer(String identifier, String department, List<String> buildings, boolean mayApproveMasters) {

    public Authorizer {
        buildings = List.copyOf(buildings);
    }

    /** A signature for every building of one department, not reaching masters. */
    public Authorizer(String identifier, String department) {
        this(identifier, department, List.of(), false);
    }

    /** A signature for some buildings of one department, not reaching masters. */
    public Authorizer(String identifier, String department, List<String> buildings) {
        this(identifier, department, buildings, false);
    }

    /** Whether this signature reaches a request for this department and building. */
    public boolean covers(String department, String building) {
        if (!department.equals(this.department)) {
            return false;
        }
        return buildings.isEmpty() || buildings.contains(building);
    }
}
