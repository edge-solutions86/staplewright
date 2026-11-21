package io.github.rkaushikdev.staplewright.control;

import java.time.LocalDate;

/**
 * One letter the shop owes somebody about a late key.
 *
 * @param tag the key that is late
 * @param keyholder who has it
 * @param level how hard this letter asks
 * @param daysLate how many days past due the key was when the letter was owed
 * @param on the day it was owed
 */
public record Notice(String tag, String keyholder, NoticeLevel level, int daysLate, LocalDate on) {

    /** Whether this letter puts a hold on the person's record: the hold step and anything past it. */
    public boolean placesHold() {
        return level.compareTo(NoticeLevel.HOLD) >= 0;
    }

    /** One line for the run sheet: {@code 41207 4 days late, reminder to kmaki}. */
    public String describe() {
        return tag + " " + daysLate + " days late, " + level.label() + " to " + keyholder;
    }
}
