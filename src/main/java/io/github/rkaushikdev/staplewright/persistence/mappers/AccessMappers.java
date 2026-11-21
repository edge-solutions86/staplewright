package io.github.rkaushikdev.staplewright.persistence.mappers;

import io.github.rkaushikdev.staplewright.campus.KeyedAlikeRegister;
import io.github.rkaushikdev.staplewright.campus.KeyedAlikeSet;
import io.github.rkaushikdev.staplewright.control.Answer;
import io.github.rkaushikdev.staplewright.control.Review;
import io.github.rkaushikdev.staplewright.control.ReviewLine;
import io.github.rkaushikdev.staplewright.control.ReviewState;
import io.github.rkaushikdev.staplewright.persistence.models.KeyedAlikeMemberRow;
import io.github.rkaushikdev.staplewright.persistence.models.KeyedAlikeSetRow;
import io.github.rkaushikdev.staplewright.persistence.models.ReviewLineRow;
import io.github.rkaushikdev.staplewright.persistence.models.ReviewRow;
import java.util.Comparator;
import java.util.List;

/**
 * Access reviews and keyed alike sets, between the tables and the domain objects.
 *
 * <p>The review is the awkward one. A review is built by naming a department and two dates and then
 * having lines put into it, so rebuilding one means walking the rows twice: once to make the header
 * and once to load the answers. There is no constructor that takes everything at once, on purpose,
 * because the front door is the only way a live review is ever built and a second one would drift
 * from it.
 *
 * <p>The answers go in and out as their own text, and a word this version does not recognise reads
 * as unanswered. That loses information, and the alternative is refusing to open a review from 2019
 * because somebody once typed a fifth kind of answer into it. A state nobody recognises reads as
 * open for the same reason.
 */
public final class AccessMappers {

    private AccessMappers() {}

    /** A review's header row and one row per line, the lines already listed on the header. */
    public record ReviewRows(ReviewRow review, List<ReviewLineRow> lines) {

        public ReviewRows {
            lines = List.copyOf(lines);
        }
    }

    /** A keyed alike set's row and one member row per door, the members already listed on the set. */
    public record AlikeRows(KeyedAlikeSetRow alike, List<KeyedAlikeMemberRow> members) {

        public AlikeRows {
            members = List.copyOf(members);
        }
    }

    private static Answer answer(String value) {
        return Answer.find(value).orElse(Answer.UNANSWERED);
    }

    private static ReviewState state(String value) {
        return ReviewState.find(value).orElse(ReviewState.OPEN);
    }

    /** A new line row on {@code review}, its answer stored as the answer's value. */
    public static ReviewLineRow reviewLineToRow(ReviewLine line, ReviewRow review) {
        ReviewLineRow row = ReviewLineRow.of(review, line.tag(), line.keyholder(), line.symbol());
        row.setAnswer(line.answer().value());
        row.setAnsweredOn(line.answeredOn().orElse(null));
        row.setNote(line.note());
        return row;
    }

    /** The review line a row holds; an answer nobody recognises reads as unanswered. */
    public static ReviewLine reviewLineFromRow(ReviewLineRow row) {
        return new ReviewLine(
                row.getTag(),
                row.getKeyholder(),
                row.getSymbol(),
                answer(row.getAnswer()),
                row.getAnsweredOn(),
                row.getNote());
    }

    /** A header and its lines. The caller writes the header and then each line. */
    public static ReviewRows reviewToRows(Review review) {
        ReviewRow header = new ReviewRow(review.department(), review.openedOn(), review.dueOn());
        header.setState(review.state().value());
        header.setClosedOn(review.closedOn().orElse(null));
        List<ReviewLineRow> lines = review.lines().stream()
                .map(line -> reviewLineToRow(line, header))
                .toList();
        return new ReviewRows(header, lines);
    }

    /** Build the header, then put the lines and the state back through the loading door. */
    public static Review reviewFromRows(ReviewRow row) {
        Review review = new Review(row.getDepartment(), row.getOpenedOn(), row.getDueOn());
        for (ReviewLineRow line : row.getLines()) {
            review.load(reviewLineFromRow(line));
        }
        review.loadState(state(row.getState()), row.getClosedOn());
        return review;
    }

    /** A set's row and a member row for each door, in door order. */
    public static AlikeRows alikeToRows(KeyedAlikeSet alike) {
        KeyedAlikeSetRow header = new KeyedAlikeSetRow(alike.name());
        header.setNote(alike.note());
        List<KeyedAlikeMemberRow> members = alike.openings().stream()
                .sorted()
                .map(number -> KeyedAlikeMemberRow.of(header, number))
                .toList();
        return new AlikeRows(header, members);
    }

    /** The keyed alike set a row holds, its doors in door order. */
    public static KeyedAlikeSet alikeFromRow(KeyedAlikeSetRow row) {
        return new KeyedAlikeSet(row.getName(), row.openings(), row.getNote());
    }

    /** Every keyed alike set, added by name. */
    public static KeyedAlikeRegister alikeRegisterFromRows(List<KeyedAlikeSetRow> rows) {
        KeyedAlikeRegister register = new KeyedAlikeRegister();
        rows.stream()
                .sorted(Comparator.comparing(KeyedAlikeSetRow::getName))
                .forEach(row -> register.add(alikeFromRow(row)));
        return register;
    }
}
