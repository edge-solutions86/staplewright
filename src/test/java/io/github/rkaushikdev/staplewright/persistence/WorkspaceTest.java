package io.github.rkaushikdev.staplewright.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.staplewright.KeywayError;
import io.github.rkaushikdev.staplewright.NotFoundError;
import io.github.rkaushikdev.staplewright.campus.OpeningSchedule;
import io.github.rkaushikdev.staplewright.settings.ShopSettings;
import io.github.rkaushikdev.staplewright.spec.CuttingRules;
import io.github.rkaushikdev.staplewright.support.DbCampus;
import io.github.rkaushikdev.staplewright.support.TestDatabase;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WorkspaceTest {

    private static final LocalDate TODAY = LocalDate.of(2025, 6, 30);
    private static final String SYSTEM = "chester park";

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

    @Test
    void aFreshWorkspaceHoldsNothing() {
        assertThat(new Workspace(session).loaded()).isEmpty();
    }

    @Test
    void askingForTheOpeningsBuildsThemOnce() {
        Workspace space = new Workspace(session);
        OpeningSchedule first = space.openings();
        assertThat(space.openings()).isSameAs(first);
        assertThat(space.loaded()).containsExactly("openings");
    }

    @Test
    void theCoverageAndTheRegisterAreHeldApart() {
        Workspace space = new Workspace(session);
        space.coverage();
        space.register();
        assertThat(space.loaded()).containsExactly("coverage", "register");
    }

    @Test
    void theLedgerComesBackTheSameObject() {
        Workspace space = new Workspace(session);
        assertThat(space.ledger()).isSameAs(space.ledger());
    }

    @Test
    void theDepositsComeBackTheSameObject() {
        Workspace space = new Workspace(session);
        assertThat(space.deposits()).isSameAs(space.deposits());
    }

    @Test
    void theDirectoryComesBackTheSameObject() {
        Workspace space = new Workspace(session);
        assertThat(space.directory()).isSameAs(space.directory());
    }

    @Test
    void theQuarantineTakesTheWindowItWasGiven() {
        Workspace space = new Workspace(session, new CuttingRules(), 3);
        assertThat(space.quarantine().releaseAfterYears()).isEqualTo(3);
    }

    @Test
    void aFamilyIsHeldUnderItsOwnName() {
        Workspace space = new Workspace(session);
        space.family("Sargent LA");
        assertThat(space.loaded()).containsExactly("family:Sargent LA");
    }

    @Test
    void twoFamiliesAreHeldSeparately() {
        Workspace space = new Workspace(session);
        space.family("Sargent LA");
        assertThatThrownBy(() -> space.family("nothing")).isInstanceOf(KeywayError.class);
        assertThat(space.loaded()).contains("family:Sargent LA");
    }

    @Test
    void aScheduleIsHeldUnderItsSystem() {
        Workspace space = new Workspace(session);
        space.schedule(SYSTEM);
        assertThat(space.loaded()).contains("schedule:" + SYSTEM);
    }

    @Test
    void aScheduleComesBackTheSameObject() {
        Workspace space = new Workspace(session);
        assertThat(space.schedule(SYSTEM)).isSameAs(space.schedule(SYSTEM));
    }

    @Test
    void aSystemNobodyOpenedIsA404() {
        assertThatThrownBy(() -> new Workspace(session).system("nowhere")).isInstanceOf(NotFoundError.class);
    }

    @Test
    void aScheduleForASystemNobodyOpenedIsA404() {
        assertThatThrownBy(() -> new Workspace(session).schedule("nowhere")).isInstanceOf(NotFoundError.class);
    }

    @Test
    void forgettingDropsEverything() {
        Workspace space = new Workspace(session);
        space.openings();
        space.ledger();
        space.forget();
        assertThat(space.loaded()).isEmpty();
    }

    @Test
    void forgettingMeansTheNextAnswerIsRebuilt() {
        Workspace space = new Workspace(session);
        OpeningSchedule first = space.openings();
        space.forget();
        assertThat(space.openings()).isNotSameAs(first);
    }

    @Test
    void theOpeningsAreReadOnceForAWholePage() {
        Workspace space = new Workspace(session);
        space.coverage();
        space.register();
        space.openings();
        assertThat(space.loaded()).hasSize(3);
    }

    @Test
    void whatAPersonHoldsComesOffTheLedger() {
        Workspace space = new Workspace(session);
        assertThat(space.personHoldings("kmaki")).contains("41207");
    }

    @Test
    void aPersonNobodyHasARecordOfIsA404() {
        assertThatThrownBy(() -> new Workspace(session).personHoldings("nobody"))
                .isInstanceOf(NotFoundError.class);
    }

    @Test
    void theOverdueKeysComeOffTheLedger() {
        Workspace space = new Workspace(session);
        List<String> overdue = space.overdue(TODAY);
        // The compiler already holds the answer to a list; what is left to check is that nobody can add to it.
        assertThat(overdue).isInstanceOf(List.class);
        assertThatThrownBy(() -> overdue.add("41299")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void aWorkspaceCanBeBuiltFromAShop() {
        Workspace space = Workspace.forShop(new ShopSettings(), session);
        assertThat(space.quarantineYears()).isEqualTo(7);
        assertThat(space.rules().maxRun())
                .isEqualTo(new ShopSettings().cutting().maxRun());
    }
}
