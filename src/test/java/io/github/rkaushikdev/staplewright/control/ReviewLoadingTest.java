package io.github.rkaushikdev.staplewright.control;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.staplewright.ConflictError;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ReviewLoadingTest {

    private static final LocalDate OPENED = LocalDate.of(2025, 9, 1);
    private static final LocalDate DUE = LocalDate.of(2025, 10, 1);
    private static final LocalDate CLOSED = LocalDate.of(2025, 10, 3);

    @Test
    void aStoredReviewComesBackInTheStateItReached() {
        Review review = new Review("geography", OPENED, DUE);
        review.load(new ReviewLine("41207", "kmaki", "AA1").answered(Answer.CONFIRMED, DUE, ""));
        review.loadState(ReviewState.CLOSED, Optional.of(CLOSED));
        assertThat(review.state()).isSameAs(ReviewState.CLOSED);
        assertThat(review.closedOn()).contains(CLOSED);
        assertThat(review.confirmed()).hasSize(1);
        assertThatThrownBy(() -> review.close(CLOSED)).isInstanceOf(ConflictError.class);
    }
}
