package io.github.rkaushikdev.staplewright.rekey;

/**
 * One door on a technician's route, with where it is and how long it takes.
 *
 * @param serial the cylinder to pull
 * @param opening the door it sits in
 * @param building the building code
 * @param floor the floor, as the building signs it
 * @param minutes the time at the door
 */
public record Stop(String serial, String opening, String building, String floor, int minutes) {

    /** One line of the route: {@code MOO 2  MOO-214  C-1001  20 min}. */
    public String describe() {
        return building + " " + floor + "  " + opening + "  " + serial + "  " + minutes + " min";
    }
}
