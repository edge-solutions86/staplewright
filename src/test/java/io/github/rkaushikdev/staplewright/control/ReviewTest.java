package io.github.rkaushikdev.staplewright.control;

import static io.github.rkaushikdev.staplewright.support.People.TODAY;
import static io.github.rkaushikdev.staplewright.support.People.aDirectory;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.staplewright.ConflictError;
import io.github.rkaushikdev.staplewright.InputError;
import io.github.rkaushikdev.staplewright.NotFoundError;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReviewTest {

    private static final LocalDate OPENED = LocalDate.of(2025, 9, 1);
    private static final LocalDate DUE = LocalDate.of(2025, 10, 1);
    private static final LocalDate LATE = LocalDate.of(2025, 11, 1);

    private static KeyLedger aLedger() {
        KeyLedger ledger = new KeyLedger(aDirectory());
        ledger.issue("41207", "AA1", "kmaki", TODAY);
        ledger.issue("41208", "AA", "kmaki", TODAY);
        ledger.issue("41209", "AA1", "dsalo", TODAY);
        ledger.issue("41301", "B1", "phamm", TODAY);
        return ledger;
    }

    private static Review aReview() {
        return aReview("geography");
    }

    private static Review aReview(String department) {
        Review review = new Review(department, OPENED, DUE);
        review.buildFrom(aLedger());
        return review;
    }

    @Test
    void aReviewCoversADepartmentAndAWindow() {
        assertThatThrownBy(() -> new Review("  ", OPENED, DUE)).isInstanceOf(InputError.class);
        assertThatThrownBy(() -> new Review("geography", DUE, OPENED)).isInstanceOf(ConflictError.class);
    }

    @Test
    void aReviewListsWhatTheDepartmentHolds() {
        Review review = aReview();
        assertThat(review.size()).isEqualTo(3);
        assertThat(review.contains("41301")).isFalse();
    }

    @Test
    void buildingTwiceAddsNothingNew() {
        Review review = aReview();
        assertThat(review.buildFrom(aLedger())).isZero();
    }

    @Test
    void aDepartmentHoldingNothingGetsAnEmptyList() {
        Review review = new Review("physics", OPENED, DUE);
        assertThat(review.buildFrom(aLedger())).isZero();
        assertThat(review.size()).isZero();
    }

    @Test
    void aNewLineIsUnanswered() {
        Review review = aReview();
        ReviewLine line = review.line("41207");
        assertThat(line.isAnswered()).isFalse();
        assertThat(line.wantsReturn()).isFalse();
    }

    @Test
    void confirmingAKeyRecordsTheDay() {
        Review review = aReview();
        ReviewLine line = review.answer("41207", Answer.CONFIRMED, DUE);
        assertThat(line.answer()).isSameAs(Answer.CONFIRMED);
        assertThat(line.answeredOn()).contains(DUE);
        assertThat(line.wantsReturn()).isFalse();
    }

    @Test
    void askingForAKeyBackWantsAReturn() {
        Review review = aReview();
        assertThat(review.answer("41207", Answer.RETURN, DUE).wantsReturn()).isTrue();
    }

    @Test
    void aKeyTheDepartmentDoesNotRecogniseAlsoWantsAReturn() {
        Review review = aReview();
        assertThat(review.answer("41207", Answer.UNKNOWN, DUE).wantsReturn()).isTrue();
    }

    @Test
    void anAnswerSaysSomething() {
        Review review = aReview();
        assertThatThrownBy(() -> review.answer("41207", Answer.UNANSWERED, DUE)).isInstanceOf(InputError.class);
    }

    @Test
    void aKeyIsAnsweredOnce() {
        Review review = aReview();
        review.answer("41207", Answer.CONFIRMED, DUE);
        assertThatThrownBy(() -> review.answer("41207", Answer.RETURN, DUE)).isInstanceOf(ConflictError.class);
    }

    @Test
    void aKeyThatIsNotOnTheList() {
        Review review = aReview();
        assertThatThrownBy(() -> review.answer("41301", Answer.CONFIRMED, DUE)).isInstanceOf(NotFoundError.class);
    }

    @Test
    void theThreeListsPartitionTheReview() {
        Review review = aReview();
        review.answer("41207", Answer.CONFIRMED, DUE);
        review.answer("41208", Answer.RETURN, DUE);
        assertThat(review.confirmed()).hasSize(1);
        assertThat(review.toReturn()).hasSize(1);
        assertThat(review.unanswered()).hasSize(1);
    }

    @Test
    void aReviewIsCompleteWhenEverythingIsAnswered() {
        Review review = aReview();
        assertThat(review.isComplete()).isFalse();
        for (String tag : List.of("41207", "41208", "41209")) {
            review.answer(tag, Answer.CONFIRMED, DUE);
        }
        assertThat(review.isComplete()).isTrue();
    }

    @Test
    void anEmptyReviewIsNotComplete() {
        assertThat(new Review("physics", OPENED, DUE).isComplete()).isFalse();
    }

    @Test
    void theProgressReadsAsAFraction() {
        Review review = aReview();
        assertThat(review.progress()).isEqualTo(0.0);
        review.answer("41207", Answer.CONFIRMED, DUE);
        assertThat(review.progress()).isEqualTo(0.333);
    }

    @Test
    void anEmptyReviewIsAllTheWayThrough() {
        assertThat(new Review("physics", OPENED, DUE).progress()).isEqualTo(1.0);
    }

    @Test
    void aClosedReviewTakesNoMoreAnswers() {
        Review review = aReview();
        review.close(LATE);
        assertThat(review.state()).isSameAs(ReviewState.CLOSED);
        assertThatThrownBy(() -> review.answer("41207", Answer.CONFIRMED, LATE)).isInstanceOf(ConflictError.class);
    }

    @Test
    void aReviewClosesOnce() {
        Review review = aReview();
        review.close(LATE);
        assertThatThrownBy(() -> review.close(LATE)).isInstanceOf(ConflictError.class);
    }

    @Test
    void anAbandonedReviewSaysWhy() {
        Review review = aReview();
        assertThatThrownBy(() -> review.abandon(LATE, "  ")).isInstanceOf(InputError.class);
        review.abandon(LATE, "the department was merged into geology");
        assertThat(review.state()).isSameAs(ReviewState.ABANDONED);
    }

    @Test
    void aClosedReviewCannotBeRebuilt() {
        Review review = aReview();
        review.close(LATE);
        assertThatThrownBy(() -> review.buildFrom(aLedger())).isInstanceOf(ConflictError.class);
    }

    @Test
    void aReviewIsOverdueOnlyWhileItIsOpen() {
        Review review = aReview();
        assertThat(review.overdueOn(DUE)).isFalse();
        assertThat(review.overdueOn(LATE)).isTrue();
        review.close(LATE);
        assertThat(review.overdueOn(LATE)).isFalse();
    }

    @Test
    void theSummaryCountsTheThreeLists() {
        Review review = aReview();
        review.answer("41207", Answer.CONFIRMED, DUE);
        assertThat(review.summary()).isEqualTo("geography: 1 confirmed, 0 back, 2 unanswered");
    }

    @Test
    void theChaseListNamesDepartmentsThatOweAnswers() {
        Review first = aReview();
        Review second = new Review("facilities", OPENED, DUE);
        second.buildFrom(aLedger());
        for (String tag : List.of("41301")) {
            second.answer(tag, Answer.CONFIRMED, DUE);
        }
        assertThat(Review.chaseList(List.of(first, second), LATE)).containsExactly("geography");
    }

    @Test
    void nothingIsChasedBeforeTheDueDate() {
        assertThat(Review.chaseList(List.of(aReview()), DUE)).isEmpty();
    }

    @Test
    void theKeysToCollectComeFromEveryReview() {
        Review first = aReview();
        first.answer("41207", Answer.RETURN, DUE);
        Review second = new Review("facilities", OPENED, DUE);
        second.buildFrom(aLedger());
        second.answer("41301", Answer.UNKNOWN, DUE);
        assertThat(Review.keysToCollect(List.of(first, second))).containsExactly("41207", "41301");
    }

    @Test
    void aReviewCanBeFoundByDepartment() {
        Review review = aReview();
        assertThat(Review.reviewFor(List.of(review), "geography")).isSameAs(review);
        assertThatThrownBy(() -> Review.reviewFor(List.of(review), "physics")).isInstanceOf(NotFoundError.class);
    }
}
