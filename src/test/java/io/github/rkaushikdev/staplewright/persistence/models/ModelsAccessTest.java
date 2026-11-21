package io.github.rkaushikdev.staplewright.persistence.models;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.staplewright.persistence.Schema;
import io.github.rkaushikdev.staplewright.persistence.Session;
import io.github.rkaushikdev.staplewright.support.TestDatabase;
import jakarta.persistence.PersistenceException;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ModelsAccessTest {

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

    private ReviewRow aReview() {
        return aReview(review -> {});
    }

    private ReviewRow aReview(Consumer<ReviewRow> overrides) {
        ReviewRow row = new ReviewRow("geography", OPENED, DUE);
        overrides.accept(row);
        session.add(row);
        session.flush();
        return row;
    }

    private KeyedAlikeSetRow aSet() {
        return aSet("moore cupboards");
    }

    private KeyedAlikeSetRow aSet(String name) {
        KeyedAlikeSetRow row = new KeyedAlikeSetRow(name);
        session.add(row);
        session.flush();
        return row;
    }

    @Test
    void theAccessTablesAreOnTheMetadata() {
        assertThat(Schema.tableNames())
                .contains("access_reviews", "access_review_lines", "keyed_alike_sets", "keyed_alike_members");
    }

    @Test
    void aReviewRoundTrips() {
        ReviewRow row = aReview();
        assertThat(row.isOpen()).isTrue();
        assertThat(row.getLines()).isEmpty();
        assertThat(row.toString()).contains("geography");
    }

    @Test
    void aReviewIsDueAfterItOpens() {
        assertThatThrownBy(() -> aReview(review -> review.setDueOn(LocalDate.of(2025, 8, 1))))
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    void aClosedReviewNeedsADate() {
        assertThatThrownBy(() -> aReview(review -> review.setState("closed"))).isInstanceOf(PersistenceException.class);
    }

    @Test
    void aClosedReviewWithADateIsFine() {
        ReviewRow row = aReview(review -> {
            review.setState("closed");
            review.setClosedOn(DUE);
        });
        assertThat(row.isOpen()).isFalse();
    }

    @Test
    void aDepartmentIsReviewedOncePerOpeningDate() {
        aReview();
        assertThatThrownBy(this::aReview).isInstanceOf(PersistenceException.class);
    }

    @Test
    void theSameDepartmentNextYearIsANewReview() {
        aReview();
        aReview(review -> {
            review.setOpenedOn(LocalDate.of(2026, 9, 1));
            review.setDueOn(LocalDate.of(2026, 10, 1));
        });
        assertThat(session.all(ReviewRow.class)).hasSize(2);
    }

    @Test
    void linesHangOffAReview() {
        ReviewRow review = aReview();
        session.addAll(List.of(
                ReviewLineRow.of(review, "41207", "kmaki", "AA1"), ReviewLineRow.of(review, "41208", "kmaki", "AA")));
        session.flush();
        session.refresh(review);
        assertThat(review.getLines()).hasSize(2);
        assertThat(review.getLines().get(0).isAnswered()).isFalse();
    }

    @Test
    void anAnsweredLineNeedsADate() {
        ReviewRow review = aReview();
        ReviewLineRow line = ReviewLineRow.of(review, "41207", "kmaki", "AA1");
        line.setAnswer("confirmed");
        assertThatThrownBy(() -> {
                    session.add(line);
                    session.flush();
                })
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    void anAnsweredLineWithADateIsFine() {
        ReviewRow review = aReview();
        ReviewLineRow row = ReviewLineRow.of(review, "41207", "kmaki", "AA1");
        row.setAnswer("confirmed");
        row.setAnsweredOn(DUE);
        session.add(row);
        session.flush();
        assertThat(row.isAnswered()).isTrue();
        assertThat(row.toString()).contains("confirmed");
    }

    @Test
    void aKeyAppearsOnceOnAReview() {
        ReviewRow review = aReview();
        session.add(ReviewLineRow.of(review, "41207", "kmaki", "AA1"));
        session.flush();
        assertThatThrownBy(() -> {
                    session.add(ReviewLineRow.of(review, "41207", "dsalo", "AA1"));
                    session.flush();
                })
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    void deletingAReviewTakesItsLines() {
        ReviewRow review = aReview();
        session.add(ReviewLineRow.of(review, "41207", "kmaki", "AA1"));
        session.flush();
        session.delete(review);
        session.flush();
        assertThat(session.all(ReviewLineRow.class)).isEmpty();
    }

    @Test
    void aSetHoldsItsDoors() {
        KeyedAlikeSetRow alike = aSet();
        session.addAll(List.of(
                new KeyedAlikeMemberRow(alike.getName(), "MOO-214"),
                new KeyedAlikeMemberRow(alike.getName(), "MOO-216")));
        session.flush();
        session.refresh(alike);
        assertThat(alike.openings()).containsExactly("MOO-214", "MOO-216");
        assertThat(alike.toString()).contains("moore cupboards");
    }

    @Test
    void aDoorBelongsToOneSetAndTheDatabaseSaysSo() {
        KeyedAlikeSetRow first = aSet("cupboards");
        KeyedAlikeSetRow second = aSet("stores");
        session.add(new KeyedAlikeMemberRow(first.getName(), "MOO-214"));
        session.flush();
        assertThatThrownBy(() -> {
                    session.add(new KeyedAlikeMemberRow(second.getName(), "MOO-214"));
                    session.flush();
                })
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    void aMemberNeedsADoorThatExists() {
        KeyedAlikeSetRow alike = aSet();
        assertThatThrownBy(() -> {
                    session.add(new KeyedAlikeMemberRow(alike.getName(), "ZZZ-1"));
                    session.flush();
                })
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    void deletingASetTakesItsMembers() {
        KeyedAlikeSetRow alike = aSet();
        session.add(KeyedAlikeMemberRow.of(alike, "MOO-214"));
        session.flush();
        session.delete(alike);
        session.flush();
        assertThat(session.all(KeyedAlikeMemberRow.class)).isEmpty();
    }
}
