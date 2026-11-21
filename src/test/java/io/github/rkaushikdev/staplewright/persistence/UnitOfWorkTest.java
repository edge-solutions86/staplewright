package io.github.rkaushikdev.staplewright.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.staplewright.ConflictError;
import io.github.rkaushikdev.staplewright.persistence.models.ChartRow;
import io.github.rkaushikdev.staplewright.support.TestDatabase;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UnitOfWorkTest {

    private Supplier<Session> sessions;

    @BeforeEach
    void open() {
        sessions = TestDatabase.fresh()::session;
    }

    private static ChartRow aChart() {
        return aChart("LA six pin");
    }

    private static ChartRow aChart(String family) {
        return new ChartRow("Sargent", family, 6, List.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9), 335, 15, 231, 156, 7);
    }

    private long charts() {
        return UnitOfWork.of(sessions).from(work -> work.charts().count());
    }

    @Test
    void leavingTheBlockCommits() {
        UnitOfWork.of(sessions).within(work -> work.charts().add(aChart()));
        assertThat(charts()).isOne();
    }

    @Test
    void theUnitSaysItCommitted() {
        UnitOfWork work = UnitOfWork.of(sessions);
        work.within(unit -> unit.charts().add(aChart()));
        assertThat(work.committed()).isTrue();
        assertThat(work.rolledBack()).isFalse();
    }

    @Test
    void raisingOutOfTheBlockRollsBack() {
        assertThatThrownBy(() -> UnitOfWork.of(sessions).within(work -> {
                    work.charts().add(aChart());
                    throw new IllegalStateException("something went wrong halfway");
                }))
                .isInstanceOf(IllegalStateException.class);
        assertThat(charts()).isZero();
    }

    @Test
    void theUnitSaysItRolledBack() {
        UnitOfWork work = UnitOfWork.of(sessions);
        assertThatThrownBy(() -> work.within(unit -> {
                    throw new IllegalStateException("no");
                }))
                .isInstanceOf(IllegalStateException.class);
        assertThat(work.rolledBack()).isTrue();
        assertThat(work.committed()).isFalse();
    }

    @Test
    void rollingBackByHandStopsTheCommit() {
        UnitOfWork.of(sessions).within(work -> {
            work.charts().add(aChart());
            work.rollback();
        });
        assertThat(charts()).isZero();
    }

    @Test
    void committingByHandInsideTheBlockSticks() {
        UnitOfWork.of(sessions).within(work -> {
            work.charts().add(aChart());
            work.commit();
            work.charts().add(aChart("60 five pin"));
            work.rollback();
        });
        assertThat(charts()).isOne();
    }

    @Test
    void aFlushGivesTheRowAnIdWithoutEndingAnything() {
        UnitOfWork.of(sessions).within(work -> {
            ChartRow row = work.charts().add(aChart());
            work.flush();
            assertThat(row.getId()).isNotNull();
            assertThat(work.committed()).isFalse();
        });
    }

    @Test
    void everyRepositoryIsOnTheUnit() {
        UnitOfWork.of(sessions).within(work -> assertThat(List.of(
                        work.charts(),
                        work.systems(),
                        work.openings(),
                        work.cylinders(),
                        work.keyholders(),
                        work.keys(),
                        work.requests(),
                        work.incidents(),
                        work.orders(),
                        work.quarantine()))
                .hasSize(10)
                .doesNotContainNull());
    }

    @Test
    void theRepositoriesShareOneSession() {
        UnitOfWork.of(sessions).within(work -> {
            assertThat(work.charts().session()).isSameAs(work.systems().session());
            assertThat(work.charts().session()).isSameAs(work.session());
        });
    }

    @Test
    void askingForTheSessionBeforeOpeningIsRefused() {
        UnitOfWork work = new UnitOfWork(sessions);
        assertThatThrownBy(work::session).isInstanceOf(ConflictError.class);
    }

    @Test
    void theSessionIsClosedOnTheWayOut() {
        UnitOfWork work = UnitOfWork.of(sessions);
        work.within(unit -> unit.charts().add(aChart()));
        assertThatThrownBy(work::session).isInstanceOf(ConflictError.class);
    }

    @Test
    void aUnitCanBeOpenedTwiceInARow() {
        UnitOfWork work = UnitOfWork.of(sessions);
        work.within(unit -> unit.charts().add(aChart()));
        work.within(unit -> unit.charts().add(aChart("60 five pin")));
        assertThat(charts()).isEqualTo(2);
    }

    @Test
    void aRollbackAfterAFlushStillUndoesTheWrite() {
        UnitOfWork.of(sessions).within(work -> {
            work.charts().add(aChart());
            work.flush();
            assertThat(work.charts().count()).isOne();
            work.rollback();
        });
        assertThat(charts()).isZero();
    }
}
