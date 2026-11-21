package io.github.rkaushikdev.staplewright.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.staplewright.ConflictError;
import io.github.rkaushikdev.staplewright.InputError;
import io.github.rkaushikdev.staplewright.NotFoundError;
import io.github.rkaushikdev.staplewright.persistence.models.CounterRow;
import io.github.rkaushikdev.staplewright.support.TestDatabase;
import jakarta.persistence.PersistenceException;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class NumberingTest {

    private Supplier<Session> sessions;
    private Session session;

    @BeforeEach
    void open() {
        Database database = TestDatabase.fresh();
        sessions = database::session;
        session = database.session();
    }

    @AfterEach
    void close() {
        session.close();
    }

    @Test
    void aSeriesHandsOutConsecutiveNumbers() {
        Numbering numbers = new Numbering(session);
        numbers.openSeries("work orders", "W-", 4, 1041);
        assertThat(numbers.next("work orders")).isEqualTo("W-1041");
        assertThat(numbers.next("work orders")).isEqualTo("W-1042");
        assertThat(numbers.next("work orders")).isEqualTo("W-1043");
    }

    @Test
    void peekingDoesNotTakeTheNumber() {
        Numbering numbers = new Numbering(session);
        numbers.openSeries("work orders", "W-", 4, 1041);
        assertThat(numbers.peek("work orders")).isEqualTo("W-1041");
        assertThat(numbers.peek("work orders")).isEqualTo("W-1041");
        assertThat(numbers.next("work orders")).isEqualTo("W-1041");
    }

    @Test
    void theWidthPadsTheNumber() {
        Numbering numbers = new Numbering(session);
        numbers.openSeries("tags", "", 6, 41207);
        assertThat(numbers.next("tags")).isEqualTo("041207");
    }

    @Test
    void aSeriesWithNoPrefixIsJustTheNumber() {
        Numbering numbers = new Numbering(session);
        numbers.openSeries("tags", "", 5, 41207);
        assertThat(numbers.next("tags")).isEqualTo("41207");
    }

    @Test
    void aNumberWiderThanThePaddingIsNotTruncated() {
        Numbering numbers = new Numbering(session);
        numbers.openSeries("tags", "", 3, 41207);
        assertThat(numbers.next("tags")).isEqualTo("41207");
    }

    @Test
    void reservingTakesARunAtOnce() {
        Numbering numbers = new Numbering(session);
        numbers.openSeries("tags", "", 5, 41207);
        assertThat(numbers.reserve("tags", 3)).containsExactly("41207", "41208", "41209");
        assertThat(numbers.next("tags")).isEqualTo("41210");
    }

    @Test
    void reservingNothingIsRefused() {
        Numbering numbers = new Numbering(session);
        numbers.openSeries("tags", "", 4, 1);
        assertThatThrownBy(() -> numbers.reserve("tags", 0)).isInstanceOf(InputError.class);
    }

    @Test
    void twoSeriesDoNotInterfere() {
        Numbering numbers = new Numbering(session);
        numbers.openSeries("tags", "", 5, 41207);
        numbers.openSeries("work orders", "W-", 4, 1041);
        assertThat(numbers.next("tags")).isEqualTo("41207");
        assertThat(numbers.next("work orders")).isEqualTo("W-1041");
        assertThat(numbers.next("tags")).isEqualTo("41208");
    }

    @Test
    void aSeriesIsOpenedOnce() {
        Numbering numbers = new Numbering(session);
        numbers.openSeries("tags");
        assertThatThrownBy(() -> numbers.openSeries("tags")).isInstanceOf(ConflictError.class);
    }

    @Test
    void askingASeriesThatWasNeverOpened() {
        Numbering numbers = new Numbering(session);
        assertThatThrownBy(() -> numbers.next("nothing")).isInstanceOf(NotFoundError.class);
        assertThatThrownBy(() -> numbers.peek("nothing")).isInstanceOf(NotFoundError.class);
    }

    @ParameterizedTest
    @CsvSource({"' ', 4, 1", "tags, 0, 1", "tags, 4, 0"})
    void aSeriesThatMakesNoSenseIsRefused(String name, int width, int start) {
        assertThatThrownBy(() -> new Numbering(session).openSeries(name, "", width, start))
                .isInstanceOf(InputError.class);
    }

    @Test
    void theSeriesCanBeListed() {
        Numbering numbers = new Numbering(session);
        numbers.openSeries("work orders", "W-", 4, 1);
        numbers.openSeries("tags", "", 5, 1);
        assertThat(numbers.allSeries().stream().map(CounterRow::getName)).containsExactly("tags", "work orders");
    }

    @Test
    void theCounterRowReadsBackItsOwnState() {
        Numbering numbers = new Numbering(session);
        numbers.openSeries("tags", "", 5, 41207);
        numbers.next("tags");
        CounterRow row = numbers.series("tags");
        assertThat(row.getNextValue()).isEqualTo(41208);
        assertThat(row.toString()).contains("41208");
    }

    @Test
    void aRolledBackTransactionPutsTheNumberBack() {
        UnitOfWork.of(sessions).within(work -> new Numbering(work.session()).openSeries("tags", "", 5, 41207));
        assertThatThrownBy(() -> UnitOfWork.of(sessions).within(work -> {
                    assertThat(new Numbering(work.session()).next("tags")).isEqualTo("41207");
                    throw new IllegalStateException("the clerk changed their mind");
                }))
                .isInstanceOf(IllegalStateException.class);
        UnitOfWork.of(sessions).within(work -> assertThat(new Numbering(work.session()).next("tags"))
                .isEqualTo("41207"));
    }

    @Test
    void aCommittedNumberStaysTaken() {
        UnitOfWork.of(sessions).within(work -> new Numbering(work.session()).openSeries("tags", "", 5, 41207));
        UnitOfWork.of(sessions).within(work -> assertThat(new Numbering(work.session()).next("tags"))
                .isEqualTo("41207"));
        UnitOfWork.of(sessions).within(work -> assertThat(new Numbering(work.session()).next("tags"))
                .isEqualTo("41208"));
    }

    @Test
    void aCounterCannotBeWoundBelowOne() {
        CounterRow row = new CounterRow("tags");
        row.setNextValue(0);
        assertThatThrownBy(() -> {
                    session.add(row);
                    session.flush();
                })
                .isInstanceOf(PersistenceException.class);
    }
}
