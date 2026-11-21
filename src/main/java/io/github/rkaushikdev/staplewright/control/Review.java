package io.github.rkaushikdev.staplewright.control;

import io.github.rkaushikdev.staplewright.ConflictError;
import io.github.rkaushikdev.staplewright.Facts;
import io.github.rkaushikdev.staplewright.InputError;
import io.github.rkaushikdev.staplewright.Lifecycle;
import io.github.rkaushikdev.staplewright.NotFoundError;
import io.github.rkaushikdev.staplewright.Registry;
import io.github.rkaushikdev.staplewright.util.Numbers;
import io.github.rkaushikdev.staplewright.util.Text;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

/**
 * The annual review: one department, one year, one list of keys, where the department says who
 * should still have a key.
 *
 * <p>Once a year every department is sent a list of the keys its people are holding and asked to
 * tick each one. A key nobody ticks is not proof of anything either: it usually means the head of
 * department was on leave. So the useful output is three lists rather than one verdict. Confirmed,
 * asked back, and never answered, and the third is the one that gets chased.
 *
 * <p>The rule that earns its place is that a decision cannot be changed after the review closes. A
 * department that ticks a key in November and asks for it back in December is raising a return, not
 * amending a review, and conflating the two loses the date somebody agreed the key was needed. A
 * review walks its lines in tag order.
 */
public final class Review implements Iterable<ReviewLine> {

    private final String department;
    private final LocalDate openedOn;
    private final LocalDate dueOn;
    private ReviewState state = ReviewState.OPEN;
    private Optional<LocalDate> closedOn = Optional.empty();
    private final Registry<ReviewLine> listed = new Registry<>("review line", "tag");

    /**
     * Open a review of one department, due back on {@code dueOn}.
     *
     * @throws InputError when the department is blank
     * @throws ConflictError when it would be due before it opens
     */
    public Review(String department, LocalDate openedOn, LocalDate dueOn) {
        if (Text.isBlank(department)) {
            throw new InputError("a review covers a department");
        }
        if (dueOn.isBefore(openedOn)) {
            throw new ConflictError("a review is due after it opens", Facts.of("department", department));
        }
        this.department = department;
        this.openedOn = openedOn;
        this.dueOn = dueOn;
    }

    public String department() {
        return department;
    }

    public LocalDate openedOn() {
        return openedOn;
    }

    public LocalDate dueOn() {
        return dueOn;
    }

    public ReviewState state() {
        return state;
    }

    /** The day the review was closed or abandoned, nothing while it is open. */
    public Optional<LocalDate> closedOn() {
        return closedOn;
    }

    /**
     * Put every key the department is holding on the list, leaving alone any tag already there.
     *
     * @return how many lines were added
     */
    public int buildFrom(KeyLedger ledger) {
        Lifecycle.requireState(state, List.of(ReviewState.OPEN), "that review is closed");
        int added = 0;
        for (Keyholder person : ledger.directory().inDepartment(department)) {
            for (IssuedKey key : ledger.heldBy(person.identifier())) {
                if (listed.has(key.tag())) {
                    continue;
                }
                listed.add(key.tag(), new ReviewLine(key.tag(), key.keyholder(), key.symbol()));
                added++;
            }
        }
        return added;
    }

    /**
     * The line for one key.
     *
     * @throws NotFoundError when the key is not on the list
     */
    public ReviewLine line(String tag) {
        return listed.get(tag);
    }

    /** Put a line back as it was stored, answer and all. */
    public ReviewLine load(ReviewLine line) {
        return listed.put(line.tag(), line);
    }

    /**
     * Put the review's own state back as it was stored, the closing day with it. Used when a review
     * comes off the database, after its lines are loaded.
     */
    public void loadState(ReviewState stored, Optional<LocalDate> storedClosedOn) {
        state = stored;
        closedOn = storedClosedOn;
    }

    /** Record what the department said about one key, with nothing written against it. */
    public ReviewLine answer(String tag, Answer answer, LocalDate when) {
        return answer(tag, answer, when, "");
    }

    /**
     * Record what the department said about one key. Each key is answered once.
     *
     * @throws ConflictError when the review is closed or the key was already answered
     * @throws InputError when the answer is {@link Answer#UNANSWERED}
     * @throws NotFoundError when the key is not on the list
     */
    public ReviewLine answer(String tag, Answer answer, LocalDate when, String note) {
        Lifecycle.requireState(state, List.of(ReviewState.OPEN), "that review is closed", Facts.of("tag", tag));
        ReviewLine line = listed.get(tag);
        if (answer == Answer.UNANSWERED) {
            throw new InputError("an answer says something", Facts.of("tag", tag));
        }
        if (line.isAnswered()) {
            throw new ConflictError(
                    "that key has already been answered",
                    Facts.of("tag", tag, "answer", line.answer().value()));
        }
        return listed.put(tag, line.answered(answer, when, note));
    }

    /** Close the review. It closes once and takes no answers after. */
    public void close(LocalDate when) {
        Lifecycle.requireState(state, List.of(ReviewState.OPEN), "that review is closed");
        state = ReviewState.CLOSED;
        closedOn = Optional.of(when);
    }

    /** A reorganisation ate the department halfway through. The note says so. */
    public void abandon(LocalDate when, String note) {
        Lifecycle.requireState(state, List.of(ReviewState.OPEN), "that review is closed");
        if (Text.isBlank(note)) {
            throw new InputError("an abandoned review says why", Facts.of("department", department));
        }
        state = ReviewState.ABANDONED;
        closedOn = Optional.of(when);
    }

    /** Every line, in tag order. */
    public List<ReviewLine> lines() {
        return listed.byKey();
    }

    public List<ReviewLine> confirmed() {
        return lines().stream()
                .filter(line -> line.answer() == Answer.CONFIRMED)
                .toList();
    }

    /** The keys the department asked to give back or did not recognise. */
    public List<ReviewLine> toReturn() {
        return lines().stream().filter(ReviewLine::wantsReturn).toList();
    }

    /** The keys still owed an answer, which are the ones that get chased. */
    public List<ReviewLine> unanswered() {
        return lines().stream().filter(line -> !line.isAnswered()).toList();
    }

    /** Whether there is at least one line and every line is answered. */
    public boolean isComplete() {
        return !lines().isEmpty() && unanswered().isEmpty();
    }

    /** Whether the review is still open past its due date. */
    public boolean overdueOn(LocalDate today) {
        return state == ReviewState.OPEN && today.isAfter(dueOn);
    }

    /** How much of the list is answered, as a fraction to three places. An empty list is all done. */
    public double progress() {
        int total = lines().size();
        if (total == 0) {
            return 1.0;
        }
        return Numbers.round((total - unanswered().size()) / (double) total, 3);
    }

    /** One line for the cover page: {@code geography: 1 confirmed, 0 back, 2 unanswered}. */
    public String summary() {
        return department + ": " + confirmed().size() + " confirmed, "
                + toReturn().size() + " back, " + unanswered().size() + " unanswered";
    }

    /** Whether a key with this tag is on the list. */
    public boolean contains(String tag) {
        return listed.has(tag);
    }

    /** Every line, in tag order. */
    @Override
    public Iterator<ReviewLine> iterator() {
        return lines().iterator();
    }

    public int size() {
        return listed.size();
    }

    /** Departments whose reviews are open, overdue and still owe answers, in department order. */
    public static List<String> chaseList(Collection<Review> reviews, LocalDate today) {
        return reviews.stream()
                .filter(review ->
                        review.overdueOn(today) && !review.unanswered().isEmpty())
                .map(Review::department)
                .sorted()
                .toList();
    }

    /** Every tag a department asked to give back, across every review, each once, in tag order. */
    public static List<String> keysToCollect(Collection<Review> reviews) {
        return reviews.stream()
                .flatMap(review -> review.toReturn().stream())
                .map(ReviewLine::tag)
                .distinct()
                .sorted()
                .toList();
    }

    /**
     * The first review of one department.
     *
     * @throws NotFoundError when no review covers it
     */
    public static Review reviewFor(Collection<Review> reviews, String department) {
        return reviews.stream()
                .filter(review -> review.department().equals(department))
                .findFirst()
                .orElseThrow(
                        () -> new NotFoundError("no review for that department", Facts.of("department", department)));
    }
}
