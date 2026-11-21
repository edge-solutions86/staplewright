package io.github.rkaushikdev.staplewright.campus;

import io.github.rkaushikdev.staplewright.ConflictError;
import io.github.rkaushikdev.staplewright.Facts;
import io.github.rkaushikdev.staplewright.InputError;
import io.github.rkaushikdev.staplewright.util.Text;

/**
 * One door, hatch, gate or cabinet the shop is responsible for.
 *
 * <p>Opening is the word for a door once a locksmith is looking at it. An opening is a position,
 * not a lock: it has a building, a floor, a number painted on it and a function, and it keeps that
 * identity through four different locks over thirty years. That is why the whole system is keyed
 * on openings rather than on cylinders, and why an opening number is the thing written on every
 * work order.
 *
 * <p>The shop refuses some combinations of function and space outright, and those refusals live
 * here because they are about the door rather than about the key.
 *
 * @param number the number on the work order: {@code MOO-214}
 * @param building the code of the building it is in
 * @param floor the floor, as the building signs it
 * @param room the room number painted on or beside it
 * @param function what the lockset does when nobody holds a key
 * @param description a word about which door this is
 * @param exterior whether it faces out of the building
 */
public record Opening(
        String number,
        String building,
        String floor,
        String room,
        DoorFunction function,
        String description,
        boolean exterior) {

    /**
     * An opening, refusing one with no number or no building, and a privacy or cabinet function on
     * an exterior door.
     *
     * @throws InputError for a blank number or building
     * @throws ConflictError for a function that does not go on an exterior door
     */
    public Opening {
        if (Text.isBlank(number)) {
            throw new InputError("an opening needs a number");
        }
        if (Text.isBlank(building)) {
            throw new InputError("an opening belongs to a building", Facts.of("opening", number));
        }
        if (exterior && (function == DoorFunction.PRIVACY || function == DoorFunction.CABINET)) {
            throw new ConflictError(
                    "that function does not go on an exterior door",
                    Facts.of("opening", number, "function", function.value()));
        }
    }

    /** An interior opening with nothing written about it. */
    public Opening(String number, String building, String floor, String room, DoorFunction function) {
        this(number, building, floor, room, function, "", false);
    }

    /** An interior opening with a word about which door it is. */
    public Opening(
            String number, String building, String floor, String room, DoorFunction function, String description) {
        this(number, building, floor, room, function, description, false);
    }

    /** An opening with nothing written about it, facing out or not. */
    public Opening(String number, String building, String floor, String room, DoorFunction function, boolean exterior) {
        this(number, building, floor, room, function, "", exterior);
    }

    /** Whether the lockset takes a cylinder. */
    public boolean isKeyed() {
        return function.isKeyed();
    }

    /** The building and room, the way a crew says where it is going: {@code MOO 214}. */
    public String label() {
        return building + " " + room;
    }

    /** One line for a work order: {@code MOO-214: MOO floor 2 room 214, office}. */
    public String describe() {
        String where = building + " floor " + floor + " room " + room;
        return number + ": " + where + ", " + function.value();
    }
}
