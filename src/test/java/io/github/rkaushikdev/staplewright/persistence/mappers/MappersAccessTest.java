package io.github.rkaushikdev.staplewright.persistence.mappers;

import static io.github.rkaushikdev.staplewright.persistence.mappers.AccessMappers.alikeFromRow;
import static io.github.rkaushikdev.staplewright.persistence.mappers.AccessMappers.alikeRegisterFromRows;
import static io.github.rkaushikdev.staplewright.persistence.mappers.AccessMappers.alikeToRows;
import static io.github.rkaushikdev.staplewright.persistence.mappers.AccessMappers.reviewFromRows;
import static io.github.rkaushikdev.staplewright.persistence.mappers.AccessMappers.reviewLineFromRow;
import static io.github.rkaushikdev.staplewright.persistence.mappers.AccessMappers.reviewLineToRow;
import static io.github.rkaushikdev.staplewright.persistence.mappers.AccessMappers.reviewToRows;
import static io.github.rkaushikdev.staplewright.support.People.TODAY;
import static io.github.rkaushikdev.staplewright.support.People.aDirectory;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.staplewright.campus.KeyedAlikeRegister;
import io.github.rkaushikdev.staplewright.campus.KeyedAlikeSet;
import io.github.rkaushikdev.staplewright.control.Answer;
import io.github.rkaushikdev.staplewright.control.KeyLedger;
import io.github.rkaushikdev.staplewright.control.Review;
import io.github.rkaushikdev.staplewright.control.ReviewLine;
import io.github.rkaushikdev.staplewright.control.ReviewState;
import io.github.rkaushikdev.staplewright.persistence.Session;
import io.github.rkaushikdev.staplewright.persistence.mappers.AccessMappers.AlikeRows;
import io.github.rkaushikdev.staplewright.persistence.mappers.AccessMappers.ReviewRows;
import io.github.rkaushikdev.staplewright.persistence.models.BuildingRow;
import io.github.rkaushikdev.staplewright.persistence.models.KeyedAlikeMemberRow;
import io.github.rkaushikdev.staplewright.persistence.models.KeyedAlikeSetRow;
import io.github.rkaushikdev.staplewright.persistence.models.OpeningRow;
import io.github.rkaushikdev.staplewright.persistence.models.ReviewLineRow;
import io.github.rkaushikdev.staplewright.persistence.models.ReviewRow;
import io.github.rkaushikdev.staplewright.support.TestDatabase;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MappersAccessTest {

    private static final LocalDate OPENED = LocalDate.of(2025, 9, 1);
    private static final LocalDate DUE = LocalDate.of(2025, 10, 1);

    private Session session;

    @BeforeEach
    void open() {
        session = TestDatabase.fresh().session();
        session.add(new BuildingRow("MOO", "Moore Hall"));
        session.flush();
        session.addAll(List.of(
                new OpeningRow("MOO-214", "MOO", "2", "214", "office"),
                new OpeningRow("MOO-216", "MOO", "2", "216", "office")));
        session.flush();
    }

    @AfterEach
    void close() {
        session.close();
    }

    private static KeyLedger aLedger() {
        KeyLedger ledger = new KeyLedger(aDirectory());
        ledger.issue("41207", "AA1", "kmaki", TODAY);
        ledger.issue("41208", "AA", "kmaki", TODAY);
        return ledger;
    }

    private static Review aReview() {
        Review review = new Review("geography", OPENED, DUE);
        review.buildFrom(aLedger());
        return review;
    }

    /** A review header nobody has written, for the mappings that need no database. */
    private static ReviewRow aLooseHeader() {
        return new ReviewRow("geography", OPENED, DUE);
    }

    private ReviewRow store(Review review) {
        ReviewRows rows = reviewToRows(review);
        session.add(rows.review());
        session.flush();
        for (ReviewLineRow row : rows.lines()) {
            session.add(row);
        }
        session.flush();
        return rows.review();
    }

    private KeyedAlikeSetRow storeAlike(KeyedAlikeSet alike) {
        AlikeRows rows = alikeToRows(alike);
        session.add(rows.alike());
        session.flush();
        for (KeyedAlikeMemberRow row : rows.members()) {
            session.add(row);
        }
        session.flush();
        return rows.alike();
    }

    @Test
    void aLineSurvivesARoundTrip() {
        Review review = aReview();
        ReviewLine line = review.answer("41207", Answer.CONFIRMED, DUE, "still in 214");
        assertThat(reviewLineFromRow(reviewLineToRow(line, aLooseHeader()))).isEqualTo(line);
    }

    @Test
    void anUnansweredLineSurvives() {
        ReviewLine line = aReview().line("41207");
        assertThat(reviewLineFromRow(reviewLineToRow(line, aLooseHeader())).isAnswered())
                .isFalse();
    }

    @Test
    void anAnswerThisVersionDoesNotKnowReadsAsUnanswered() {
        ReviewLineRow row = reviewLineToRow(aReview().line("41207"), aLooseHeader());
        row.setAnswer("maybe next year");
        assertThat(reviewLineFromRow(row).answer()).isSameAs(Answer.UNANSWERED);
    }

    @Test
    void aWholeReviewSurvivesTheDatabase() {
        Review review = aReview();
        review.answer("41207", Answer.CONFIRMED, DUE);
        review.answer("41208", Answer.RETURN, DUE);
        ReviewRow header = store(review);
        Review back = reviewFromRows(header);
        assertThat(back.size()).isEqualTo(2);
        assertThat(back.confirmed()).hasSize(1);
        assertThat(back.toReturn()).hasSize(1);
    }

    @Test
    void aClosedReviewComesBackClosed() {
        Review review = aReview();
        review.close(DUE);
        ReviewRow header = store(review);
        Review back = reviewFromRows(header);
        assertThat(back.state()).isSameAs(ReviewState.CLOSED);
        assertThat(back.closedOn()).contains(DUE);
    }

    @Test
    void aLoadedReviewKeepsAnswersTheFrontDoorWouldRefuse() {
        Review review = aReview();
        review.answer("41207", Answer.CONFIRMED, DUE);
        review.close(DUE);
        Review back = reviewFromRows(store(review));
        assertThat(back.line("41207").answer()).isSameAs(Answer.CONFIRMED);
    }

    @Test
    void theDatesSurvive() {
        ReviewRow header = store(aReview());
        Review back = reviewFromRows(header);
        assertThat(back.openedOn()).isEqualTo(OPENED);
        assertThat(back.dueOn()).isEqualTo(DUE);
    }

    @Test
    void aSetSurvivesARoundTrip() {
        KeyedAlikeSet alike = new KeyedAlikeSet("cupboards", List.of("MOO-214", "MOO-216"), "the map room store");
        KeyedAlikeSetRow header = storeAlike(alike);
        session.refresh(header);
        assertThat(alikeFromRow(header)).isEqualTo(alike);
    }

    @Test
    void theMembersComeBackInDoorOrder() {
        KeyedAlikeSet alike = new KeyedAlikeSet("cupboards", List.of("MOO-216", "MOO-214"));
        KeyedAlikeSetRow header = storeAlike(alike);
        session.refresh(header);
        assertThat(alikeFromRow(header).openings()).containsExactly("MOO-214", "MOO-216");
    }

    @Test
    void aWholeRegisterComesBack() {
        storeAlike(new KeyedAlikeSet("cupboards", List.of("MOO-214", "MOO-216")));
        List<KeyedAlikeSetRow> rows = session.all(KeyedAlikeSetRow.class);
        KeyedAlikeRegister register = alikeRegisterFromRows(rows);
        assertThat(register.size()).isEqualTo(1);
        assertThat(register.setHolding("MOO-214").orElseThrow().name()).isEqualTo("cupboards");
    }
}
