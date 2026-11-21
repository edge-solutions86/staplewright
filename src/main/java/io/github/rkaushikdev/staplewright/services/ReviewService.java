package io.github.rkaushikdev.staplewright.services;

import io.github.rkaushikdev.staplewright.ConflictError;
import io.github.rkaushikdev.staplewright.Facts;
import io.github.rkaushikdev.staplewright.InputError;
import io.github.rkaushikdev.staplewright.Lifecycle;
import io.github.rkaushikdev.staplewright.NotFoundError;
import io.github.rkaushikdev.staplewright.control.Answer;
import io.github.rkaushikdev.staplewright.control.Review;
import io.github.rkaushikdev.staplewright.control.ReviewState;
import io.github.rkaushikdev.staplewright.persistence.Session;
import io.github.rkaushikdev.staplewright.persistence.Workspace;
import io.github.rkaushikdev.staplewright.persistence.mappers.AccessMappers;
import io.github.rkaushikdev.staplewright.persistence.mappers.AccessMappers.ReviewRows;
import io.github.rkaushikdev.staplewright.persistence.models.ReviewLineRow;
import io.github.rkaushikdev.staplewright.persistence.models.ReviewRow;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * One department's annual access review, from opening it to closing it.
 *
 * <p>Opening a review reads the ledger and writes a line for every key the department is holding at
 * that moment, and those lines do not move afterwards. A key issued the following week is not on
 * this year's review, which is correct: the review is a statement about a date, and a list that
 * kept changing under the department would never be answerable.
 */
public final class ReviewService {

    private static final List<String> OPEN = List.of(ReviewState.OPEN.value());

    private final Session session;

    public ReviewService(Session session) {
        this.session = session;
    }

    public Session session() {
        return session;
    }

    /**
     * Open a review and freeze the department's holdings onto it.
     *
     * @throws ConflictError when the department was already reviewed on that day
     */
    public ReviewRow openReview(String department, LocalDate openedOn, LocalDate dueOn) {
        if (find(department, openedOn).isPresent()) {
            throw new ConflictError(
                    "that department was already reviewed on that day",
                    Facts.of("department", department, "opened_on", openedOn.toString()));
        }
        Review review = new Review(department, openedOn, dueOn);
        review.buildFrom(new Workspace(session).ledger());
        ReviewRows rows = AccessMappers.reviewToRows(review);
        session.add(rows.review());
        session.flush();
        session.addAll(rows.lines());
        session.flush();
        return rows.review();
    }

    /** The review as it stands, its answers included. */
    public Review review(String department, LocalDate openedOn) {
        return AccessMappers.reviewFromRows(mustFind(department, openedOn));
    }

    /** Answer for one key, with no note. */
    public ReviewLineRow answer(String department, LocalDate openedOn, String tag, Answer answer, LocalDate when) {
        return answer(department, openedOn, tag, answer, when, "");
    }

    /**
     * Answer for one key on an open review. Each key is answered once, and an answer has to say
     * something.
     */
    public ReviewLineRow answer(
            String department, LocalDate openedOn, String tag, Answer answer, LocalDate when, String note) {
        ReviewRow header = mustFind(department, openedOn);
        Lifecycle.requireState(header.getState(), OPEN, "that review is closed", Facts.of("department", department));
        if (answer == Answer.UNANSWERED) {
            throw new InputError("an answer says something", Facts.of("tag", tag));
        }
        ReviewLineRow line = line(header.getId(), tag);
        if (line.isAnswered()) {
            throw new ConflictError("that key has already been answered", Facts.of("tag", tag));
        }
        line.setAnswer(answer.value());
        line.setAnsweredOn(when);
        line.setNote(note);
        session.flush();
        return line;
    }

    /** Close an open review. */
    public ReviewRow close(String department, LocalDate openedOn, LocalDate when) {
        ReviewRow header = mustFind(department, openedOn);
        Lifecycle.requireState(header.getState(), OPEN, "that review is closed", Facts.of("department", department));
        header.setState(ReviewState.CLOSED.value());
        header.setClosedOn(when);
        session.flush();
        return header;
    }

    /** Every review still open, by department. */
    public List<ReviewRow> openReviews() {
        return session.list(
                "select r from ReviewRow r where r.state = ?1 order by r.department",
                ReviewRow.class,
                ReviewState.OPEN.value());
    }

    private Optional<ReviewRow> find(String department, LocalDate openedOn) {
        return session.first(
                "select r from ReviewRow r where r.department = ?1 and r.openedOn = ?2",
                ReviewRow.class,
                department,
                openedOn);
    }

    private ReviewRow mustFind(String department, LocalDate openedOn) {
        return find(department, openedOn)
                .orElseThrow(() -> new NotFoundError(
                        "no review of that department on that day",
                        Facts.of("department", department, "opened_on", openedOn.toString())));
    }

    private ReviewLineRow line(int reviewId, String tag) {
        return session.first(
                        "select l from ReviewLineRow l where l.review.id = ?1 and l.tag = ?2",
                        ReviewLineRow.class,
                        reviewId,
                        tag)
                .orElseThrow(() -> new NotFoundError("that key is not on this review", Facts.of("tag", tag)));
    }
}
