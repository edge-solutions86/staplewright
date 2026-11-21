package io.github.rkaushikdev.staplewright.rekey;

import io.github.rkaushikdev.staplewright.InputError;
import java.util.List;

/**
 * What the shop charges and how long things take.
 *
 * @param benchMinutesPerCylinder repinning one cylinder at the bench
 * @param doorMinutesPerCylinder pulling and refitting one cylinder at the door
 * @param coreDoorMinutes swapping an interchangeable core at the door
 * @param keyCutMinutes cutting one key
 * @param travelMinutesPerBuilding getting a crew to a building and back
 * @param labourCentsPerHour what an hour of a locksmith costs
 * @param blankCents one key blank
 * @param pinCents one pin
 * @param pinsPerCylinder pins a repin uses
 */
public record RateCard(
        int benchMinutesPerCylinder,
        int doorMinutesPerCylinder,
        int coreDoorMinutes,
        int keyCutMinutes,
        int travelMinutesPerBuilding,
        int labourCentsPerHour,
        int blankCents,
        int pinCents,
        int pinsPerCylinder) {

    /** The shop's standing rates. */
    public static final RateCard DEFAULT = new RateCard();

    public RateCard {
        List<Integer> fields = List.of(
                benchMinutesPerCylinder,
                doorMinutesPerCylinder,
                coreDoorMinutes,
                keyCutMinutes,
                travelMinutesPerBuilding,
                labourCentsPerHour,
                blankCents,
                pinCents,
                pinsPerCylinder);
        if (fields.stream().anyMatch(value -> value < 0)) {
            throw new InputError("a rate card holds no negative numbers");
        }
        if (labourCentsPerHour == 0) {
            throw new InputError("labour is not free on this campus");
        }
    }

    /** The shop's standing rates. */
    public RateCard() {
        this(12, 20, 2, 3, 25, 5200, 180, 12, 12);
    }

    /** The same card with {@code benchMinutesPerCylinder} changed. */
    public RateCard withBenchMinutesPerCylinder(int value) {
        return new RateCard(
                value,
                doorMinutesPerCylinder,
                coreDoorMinutes,
                keyCutMinutes,
                travelMinutesPerBuilding,
                labourCentsPerHour,
                blankCents,
                pinCents,
                pinsPerCylinder);
    }

    /** The same card with {@code doorMinutesPerCylinder} changed. */
    public RateCard withDoorMinutesPerCylinder(int value) {
        return new RateCard(
                benchMinutesPerCylinder,
                value,
                coreDoorMinutes,
                keyCutMinutes,
                travelMinutesPerBuilding,
                labourCentsPerHour,
                blankCents,
                pinCents,
                pinsPerCylinder);
    }

    /** The same card with {@code coreDoorMinutes} changed. */
    public RateCard withCoreDoorMinutes(int value) {
        return new RateCard(
                benchMinutesPerCylinder,
                doorMinutesPerCylinder,
                value,
                keyCutMinutes,
                travelMinutesPerBuilding,
                labourCentsPerHour,
                blankCents,
                pinCents,
                pinsPerCylinder);
    }

    /** The same card with {@code keyCutMinutes} changed. */
    public RateCard withKeyCutMinutes(int value) {
        return new RateCard(
                benchMinutesPerCylinder,
                doorMinutesPerCylinder,
                coreDoorMinutes,
                value,
                travelMinutesPerBuilding,
                labourCentsPerHour,
                blankCents,
                pinCents,
                pinsPerCylinder);
    }

    /** The same card with {@code travelMinutesPerBuilding} changed. */
    public RateCard withTravelMinutesPerBuilding(int value) {
        return new RateCard(
                benchMinutesPerCylinder,
                doorMinutesPerCylinder,
                coreDoorMinutes,
                keyCutMinutes,
                value,
                labourCentsPerHour,
                blankCents,
                pinCents,
                pinsPerCylinder);
    }

    /** The same card with {@code labourCentsPerHour} changed. */
    public RateCard withLabourCentsPerHour(int value) {
        return new RateCard(
                benchMinutesPerCylinder,
                doorMinutesPerCylinder,
                coreDoorMinutes,
                keyCutMinutes,
                travelMinutesPerBuilding,
                value,
                blankCents,
                pinCents,
                pinsPerCylinder);
    }

    /** The same card with {@code blankCents} changed. */
    public RateCard withBlankCents(int value) {
        return new RateCard(
                benchMinutesPerCylinder,
                doorMinutesPerCylinder,
                coreDoorMinutes,
                keyCutMinutes,
                travelMinutesPerBuilding,
                labourCentsPerHour,
                value,
                pinCents,
                pinsPerCylinder);
    }

    /** The same card with {@code pinCents} changed. */
    public RateCard withPinCents(int value) {
        return new RateCard(
                benchMinutesPerCylinder,
                doorMinutesPerCylinder,
                coreDoorMinutes,
                keyCutMinutes,
                travelMinutesPerBuilding,
                labourCentsPerHour,
                blankCents,
                value,
                pinsPerCylinder);
    }

    /** The same card with {@code pinsPerCylinder} changed. */
    public RateCard withPinsPerCylinder(int value) {
        return new RateCard(
                benchMinutesPerCylinder,
                doorMinutesPerCylinder,
                coreDoorMinutes,
                keyCutMinutes,
                travelMinutesPerBuilding,
                labourCentsPerHour,
                blankCents,
                pinCents,
                value);
    }
}
