package io.github.rkaushikdev.staplewright.control;

import java.time.LocalDate;
import java.util.Optional;

/**
 * One key on one department's review list.
 *
 * @param tag the key
 * @param keyholder who the ledger says has it
 * @param symbol what it was cut to
 * @param answer what the department said about it
 * @param answeredOn the day the department said it
 * @param note what was written with the answer
 */
public record ReviewLine(
        String tag, String keyholder, String symbol, Answer answer, Optional<LocalDate> answeredOn, String note) {

    /** A line nobody has answered yet. */
    public ReviewLine(String tag, String keyholder, String symbol) {
        this(tag, keyholder, symbol, Answer.UNANSWERED, Optional.empty(), "");
    }

    public boolean isAnswered() {
        return answer != Answer.UNANSWERED;
    }

    /** Whether the department asked for this key back or did not recognise it. */
    public boolean wantsReturn() {
        return answer.wantsReturn();
    }

    /** The same line with the department's answer on it. */
    ReviewLine answered(Answer given, LocalDate when, String answerNote) {
        return new ReviewLine(tag, keyholder, symbol, given, Optional.of(when), answerNote);
    }
}
