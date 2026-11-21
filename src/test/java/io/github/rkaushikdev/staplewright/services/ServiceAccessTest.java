package io.github.rkaushikdev.staplewright.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.staplewright.ConflictError;
import io.github.rkaushikdev.staplewright.InputError;
import io.github.rkaushikdev.staplewright.NotFoundError;
import io.github.rkaushikdev.staplewright.campus.KeyedAlikeSet;
import io.github.rkaushikdev.staplewright.control.Answer;
import io.github.rkaushikdev.staplewright.control.Review;
import io.github.rkaushikdev.staplewright.control.ReviewState;
import io.github.rkaushikdev.staplewright.persistence.Session;
import io.github.rkaushikdev.staplewright.persistence.models.IssuedKeyRow;
import io.github.rkaushikdev.staplewright.persistence.models.ReviewLineRow;
import io.github.rkaushikdev.staplewright.persistence.models.ReviewRow;
import io.github.rkaushikdev.staplewright.support.DbCampus;
import io.github.rkaushikdev.staplewright.support.TestDatabase;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ServiceAccessTest {

    private static final LocalDate OPENED = LocalDate.of(2025, 9, 1);
    private static final LocalDate DUE = LocalDate.of(2025, 10, 1);
    private static final LocalDate CLOSED = LocalDate.of(2025, 11, 1);

    private Session session;

    @BeforeEach
    void open() {
        session = TestDatabase.fresh().session();
        DbCampus.build(session);
    }

    @AfterEach
    void close() {
        session.close();
    }

    private ReviewService anOpenReview() {
        ReviewService service = new ReviewService(session);
        service.openReview("geography", OPENED, DUE);
        return service;
    }

    private static KeyedAlikeSet alike(String name, String... openings) {
        return new KeyedAlikeSet(name, List.of(openings));
    }

    @Test
    void openingAReviewFreezesWhatTheDepartmentHolds() {
        ReviewService service = anOpenReview();
        Review review = service.review("geography", OPENED);
        assertThat(review.size()).isEqualTo(3);
        assertThat(review.contains("41207")).isTrue();
    }

    @Test
    void aKeyIssuedLaterIsNotOnThisYearsReview() {
        ReviewService service = anOpenReview();
        session.add(new IssuedKeyRow("41999", "AA1", "kmaki", DUE));
        session.flush();
        assertThat(service.review("geography", OPENED).contains("41999")).isFalse();
    }

    @Test
    void aDepartmentIsReviewedOnceOnADay() {
        ReviewService service = anOpenReview();
        assertThatThrownBy(() -> service.openReview("geography", OPENED, DUE)).isInstanceOf(ConflictError.class);
    }

    @Test
    void aReviewThatNeverHappened() {
        ReviewService service = new ReviewService(session);
        assertThatThrownBy(() -> service.review("geography", OPENED)).isInstanceOf(NotFoundError.class);
    }

    @Test
    void answeringRecordsTheDayAndTheNote() {
        ReviewService service = anOpenReview();
        ReviewLineRow line = service.answer("geography", OPENED, "41207", Answer.CONFIRMED, DUE, "still in 214");
        assertThat(line.getAnswer()).isEqualTo("confirmed");
        assertThat(line.getAnsweredOn()).contains(DUE);
        assertThat(line.getNote()).isEqualTo("still in 214");
    }

    @Test
    void anAnswerSaysSomething() {
        ReviewService service = anOpenReview();
        assertThatThrownBy(() -> service.answer("geography", OPENED, "41207", Answer.UNANSWERED, DUE))
                .isInstanceOf(InputError.class);
    }

    @Test
    void aKeyIsAnsweredOnce() {
        ReviewService service = anOpenReview();
        service.answer("geography", OPENED, "41207", Answer.CONFIRMED, DUE);
        assertThatThrownBy(() -> service.answer("geography", OPENED, "41207", Answer.RETURN, DUE))
                .isInstanceOf(ConflictError.class);
    }

    @Test
    void aKeyThatIsNotOnTheReview() {
        ReviewService service = anOpenReview();
        assertThatThrownBy(() -> service.answer("geography", OPENED, "41301", Answer.CONFIRMED, DUE))
                .isInstanceOf(NotFoundError.class);
    }

    @Test
    void aClosedReviewTakesNoMoreAnswers() {
        ReviewService service = anOpenReview();
        service.close("geography", OPENED, CLOSED);
        assertThatThrownBy(() -> service.answer("geography", OPENED, "41207", Answer.CONFIRMED, CLOSED))
                .isInstanceOf(ConflictError.class);
    }

    @Test
    void aReviewClosesOnce() {
        ReviewService service = anOpenReview();
        service.close("geography", OPENED, CLOSED);
        assertThatThrownBy(() -> service.close("geography", OPENED, CLOSED)).isInstanceOf(ConflictError.class);
    }

    @Test
    void theOpenReviewsComeBackByDepartment() {
        ReviewService service = anOpenReview();
        service.openReview("facilities", OPENED, DUE);
        assertThat(service.openReviews().stream().map(ReviewRow::getDepartment))
                .containsExactly("facilities", "geography");
    }

    @Test
    void aClosedReviewDropsOffTheOpenList() {
        ReviewService service = anOpenReview();
        service.close("geography", OPENED, CLOSED);
        assertThat(service.openReviews()).isEmpty();
    }

    @Test
    void theReviewReadsBackWithItsAnswers() {
        ReviewService service = anOpenReview();
        service.answer("geography", OPENED, "41207", Answer.RETURN, DUE);
        Review review = service.review("geography", OPENED);
        assertThat(review.toReturn()).hasSize(1);
        assertThat(review.state()).isSameAs(ReviewState.OPEN);
    }

    @Test
    void aSetCanBeWritten() {
        KeyedAlikeService service = new KeyedAlikeService(session);
        service.create(alike("cupboards", "MOO-214", "MOO-216"));
        assertThat(service.get("cupboards").size()).isEqualTo(2);
    }

    @Test
    void aDoorAlreadyInASetIsRefused() {
        KeyedAlikeService service = new KeyedAlikeService(session);
        service.create(alike("cupboards", "MOO-214", "MOO-216"));
        assertThatThrownBy(() -> service.create(alike("stores", "MOO-214", "MOO-218")))
                .isInstanceOf(ConflictError.class);
    }

    @Test
    void aSetThatIsNotThere() {
        KeyedAlikeService service = new KeyedAlikeService(session);
        assertThatThrownBy(() -> service.get("nothing")).isInstanceOf(NotFoundError.class);
    }

    @Test
    void theSetHoldingADoorCanBeFound() {
        KeyedAlikeService service = new KeyedAlikeService(session);
        service.create(alike("cupboards", "MOO-214", "MOO-216"));
        assertThat(service.setHolding("MOO-214").orElseThrow().name()).isEqualTo("cupboards");
        assertThat(service.setHolding("MOO-218")).isEmpty();
    }

    @Test
    void dissolvingASetFreesItsDoors() {
        KeyedAlikeService service = new KeyedAlikeService(session);
        service.create(alike("cupboards", "MOO-214", "MOO-216"));
        service.dissolve("cupboards");
        assertThat(service.setHolding("MOO-214")).isEmpty();
        service.create(alike("stores", "MOO-214", "MOO-218"));
        assertThat(service.get("stores").size()).isEqualTo(2);
    }

    @Test
    void dissolvingASetThatIsNotThere() {
        KeyedAlikeService service = new KeyedAlikeService(session);
        assertThatThrownBy(() -> service.dissolve("nothing")).isInstanceOf(NotFoundError.class);
    }

    @Test
    void theSetsComeBackInNameOrder() {
        KeyedAlikeService service = new KeyedAlikeService(session);
        service.create(alike("stores", "MOO-218", "MOO-110"));
        service.create(alike("cupboards", "MOO-214", "MOO-216"));
        assertThat(service.sets().stream().map(KeyedAlikeSet::name)).containsExactly("cupboards", "stores");
    }
}
