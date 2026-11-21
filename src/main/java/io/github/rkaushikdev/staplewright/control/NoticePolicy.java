package io.github.rkaushikdev.staplewright.control;

import io.github.rkaushikdev.staplewright.ConflictError;
import io.github.rkaushikdev.staplewright.Facts;
import io.github.rkaushikdev.staplewright.InputError;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

/**
 * How many days late each step of the notice ladder happens.
 *
 * @param reminderDays a friendly reminder
 * @param firstDays the first formal notice
 * @param secondDays the second
 * @param holdDays a hold on the person's account
 * @param writeOffDays the key is written off and the deposit kept
 */
public record NoticePolicy(int reminderDays, int firstDays, int secondDays, int holdDays, int writeOffDays) {

    /** The shop's standing ladder. */
    public static final NoticePolicy DEFAULT = new NoticePolicy();

    public NoticePolicy {
        List<Integer> steps = List.of(reminderDays, firstDays, secondDays, holdDays, writeOffDays);
        if (steps.stream().anyMatch(day -> day < 0)) {
            throw new InputError("a notice threshold is a number of days");
        }
        if (!steps.equals(steps.stream().sorted().toList()) || new HashSet<>(steps).size() != steps.size()) {
            throw new ConflictError("the notice steps have to get later", Facts.of("steps", steps));
        }
    }

    /** Three days, two weeks, a month, two months, four months. */
    public NoticePolicy() {
        this(3, 14, 30, 60, 120);
    }

    /** The furthest step this many days late has reached, or nothing before the reminder. */
    public Optional<NoticeLevel> levelFor(long daysLate) {
        if (daysLate >= writeOffDays) {
            return Optional.of(NoticeLevel.WRITTEN_OFF);
        }
        if (daysLate >= holdDays) {
            return Optional.of(NoticeLevel.HOLD);
        }
        if (daysLate >= secondDays) {
            return Optional.of(NoticeLevel.SECOND);
        }
        if (daysLate >= firstDays) {
            return Optional.of(NoticeLevel.FIRST);
        }
        if (daysLate >= reminderDays) {
            return Optional.of(NoticeLevel.REMINDER);
        }
        return Optional.empty();
    }
}
