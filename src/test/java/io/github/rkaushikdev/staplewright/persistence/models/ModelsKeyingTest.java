package io.github.rkaushikdev.staplewright.persistence.models;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.staplewright.keys.Bitting;
import io.github.rkaushikdev.staplewright.keys.KeySymbol;
import io.github.rkaushikdev.staplewright.persistence.Schema;
import io.github.rkaushikdev.staplewright.persistence.Session;
import io.github.rkaushikdev.staplewright.support.TestDatabase;
import jakarta.persistence.PersistenceException;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ModelsKeyingTest {

    private Session session;

    @BeforeEach
    void open() {
        session = TestDatabase.fresh().session();
    }

    @AfterEach
    void close() {
        session.close();
    }

    private static KeySymbol sym(String text) {
        return KeySymbol.parse(text);
    }

    private static Bitting b(String text) {
        return Bitting.parse(text);
    }

    private SystemRow aSystem() {
        return aSystem("chester park");
    }

    private SystemRow aSystem(String name) {
        ChartRow chart = new ChartRow(
                "Sargent", "LA six pin " + name, 6, List.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9), 335, 15, 231, 156, 7);
        session.add(chart);
        session.flush();
        SystemRow system = new SystemRow(name, chart, "Sargent LA", "L");
        session.add(system);
        session.flush();
        return system;
    }

    @Test
    void theKeyingTablesAreOnTheMetadata() {
        assertThat(Schema.tableNames())
                .contains("keying_systems", "schedule_entries", "keying_groups", "keying_group_members");
    }

    @Test
    void aSystemCarriesItsChart() {
        SystemRow system = aSystem();
        assertThat(system.getChart().getChambers()).isEqualTo(6);
        assertThat(system.getProgressionStep()).isEqualTo(2);
        assertThat(system.isArchived()).isFalse();
        assertThat(system.toString()).contains("chester park");
    }

    @Test
    void aSystemNameIsUnique() {
        aSystem();
        assertThatThrownBy(() -> {
                    session.add(new SystemRow("chester park", session.reference(ChartRow.class, 1), "Sargent LA", "L"));
                    session.flush();
                })
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    void aProgressionStepOfNothingIsRefused() {
        SystemRow system = aSystem();
        system.setProgressionStep(0);
        assertThatThrownBy(session::flush).isInstanceOf(PersistenceException.class);
    }

    @Test
    void anEntryRoundTripsItsSymbolAndCut() {
        SystemRow system = aSystem();
        session.add(ScheduleEntryRow.of(system, sym("AA1"), "LC", b("342156")));
        session.expireAll();
        ScheduleEntryRow found = session.one(ScheduleEntryRow.class);
        assertThat(found.getSymbol()).isEqualTo(sym("AA1"));
        assertThat(found.getBitting()).contains(b("342156"));
        assertThat(found.isCut()).isTrue();
        assertThat(found.toString()).contains("AA1");
    }

    @Test
    void anUncutEntryIsAllowed() {
        SystemRow system = aSystem();
        session.add(ScheduleEntryRow.of(system, sym("AA2"), "LC"));
        session.expireAll();
        ScheduleEntryRow found = session.one(ScheduleEntryRow.class);
        assertThat(found.getBitting()).isEmpty();
        assertThat(found.isCut()).isFalse();
        assertThat(found.toString()).contains("open");
    }

    @Test
    void aSymbolAppearsOnceInASystem() {
        SystemRow system = aSystem();
        session.add(ScheduleEntryRow.of(system, sym("AA1"), "LC"));
        session.flush();
        assertThatThrownBy(() -> {
                    session.add(ScheduleEntryRow.of(system, sym("AA1"), "LD"));
                    session.flush();
                })
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    void theSameSymbolInAnotherSystemIsFine() {
        SystemRow first = aSystem();
        SystemRow second = aSystem("voss hall");
        session.add(ScheduleEntryRow.of(first, sym("AA1"), "LC"));
        session.add(ScheduleEntryRow.of(second, sym("AA1"), "LC"));
        session.flush();
        assertThat(session.all(ScheduleEntryRow.class)).hasSize(2);
    }

    @Test
    void aCutAppearsOnceInASystem() {
        SystemRow system = aSystem();
        session.add(ScheduleEntryRow.of(system, sym("AA1"), "LC", b("342156")));
        session.flush();
        assertThatThrownBy(() -> {
                    session.add(ScheduleEntryRow.of(system, sym("AA2"), "LC", b("342156")));
                    session.flush();
                })
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    void anyNumberOfSymbolsCanBeUncut() {
        SystemRow system = aSystem();
        for (int number = 1; number < 5; number++) {
            session.add(ScheduleEntryRow.of(system, sym("AA" + number), "LC"));
        }
        session.flush();
        assertThat(session.all(ScheduleEntryRow.class)).hasSize(4);
    }

    @Test
    void aSystemListsItsOwnEntries() {
        SystemRow system = aSystem();
        session.add(ScheduleEntryRow.of(system, sym("AA1"), "LC"));
        session.flush();
        session.refresh(system);
        assertThat(system.getEntries().stream().map(entry -> entry.getSymbol().render()))
                .containsExactly("AA1");
    }

    @Test
    void deletingASystemTakesItsEntriesWithIt() {
        SystemRow system = aSystem();
        session.add(ScheduleEntryRow.of(system, sym("AA1"), "LC"));
        session.flush();
        session.delete(system);
        session.flush();
        assertThat(session.all(ScheduleEntryRow.class)).isEmpty();
    }

    @Test
    void aGroupHoldsItsSymbols() {
        SystemRow system = aSystem();
        GroupRow group = GroupRow.of(system, "moore offices");
        session.add(group);
        session.flush();
        session.addAll(List.of(GroupMemberRow.of(group, sym("AA1")), GroupMemberRow.of(group, sym("AA"))));
        session.flush();
        session.refresh(group);
        assertThat(group.symbols().stream().map(KeySymbol::render).sorted()).containsExactly("AA", "AA1");
        assertThat(group.toString()).contains("moore offices");
    }

    @Test
    void aGroupNameIsUniqueInASystem() {
        SystemRow system = aSystem();
        session.add(GroupRow.of(system, "moore offices"));
        session.flush();
        assertThatThrownBy(() -> {
                    session.add(GroupRow.of(system, "moore offices"));
                    session.flush();
                })
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    void aSymbolIsInAGroupOnce() {
        SystemRow system = aSystem();
        GroupRow group = GroupRow.of(system, "moore offices");
        session.add(group);
        session.flush();
        session.add(GroupMemberRow.of(group, sym("AA1")));
        session.flush();
        assertThatThrownBy(() -> {
                    session.add(GroupMemberRow.of(group, sym("AA1")));
                    session.flush();
                })
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    void deletingAGroupTakesItsMembers() {
        SystemRow system = aSystem();
        GroupRow group = GroupRow.of(system, "moore offices");
        session.add(group);
        session.flush();
        session.add(GroupMemberRow.of(group, sym("AA1")));
        session.flush();
        system.removeGroup(group);
        session.delete(group);
        session.flush();
        assertThat(session.all(GroupMemberRow.class)).isEmpty();
    }

    @Test
    void aGroupNeedsASystemThatExists() {
        assertThatThrownBy(() -> {
                    session.add(GroupRow.of(session.reference(SystemRow.class, 999), "nowhere"));
                    session.flush();
                })
                .isInstanceOf(PersistenceException.class);
    }
}
