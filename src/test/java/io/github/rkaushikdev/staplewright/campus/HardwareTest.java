package io.github.rkaushikdev.staplewright.campus;

import static io.github.rkaushikdev.staplewright.support.Campus.aCampus;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import io.github.rkaushikdev.staplewright.ConflictError;
import io.github.rkaushikdev.staplewright.InputError;
import io.github.rkaushikdev.staplewright.NotFoundError;
import java.util.List;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;

class HardwareTest {

    private static Cylinder aCylinder(String serial) {
        return new Cylinder(serial, CylinderFormat.MORTISE, "LC", 6, "Sargent");
    }

    private static Cylinder aCylinder(String serial, UnaryOperator<Cylinder.Builder> overrides) {
        return overrides.apply(aCylinder(serial).toBuilder()).build();
    }

    private static HardwareRegister aRegister() {
        HardwareRegister register = new HardwareRegister(aCampus());
        for (String serial : List.of("C-1001", "C-1002", "C-1003", "C-1004")) {
            register.add(aCylinder(serial));
        }
        register.add(aCylinder(
                "K-2001",
                cylinder -> cylinder.format(CylinderFormat.SFIC).chambers(7).section("A2")));
        return register;
    }

    private static List<String> serials(Iterable<Cylinder> cylinders) {
        return StreamSupport.stream(cylinders.spliterator(), false)
                .map(Cylinder::serial)
                .toList();
    }

    @Test
    void aNewCylinderStartsOnTheShelf() {
        HardwareRegister register = aRegister();
        assertThat(register.cylinder("C-1001").isInstalled()).isFalse();
        assertThat(register.onTheShelf()).hasSize(5);
    }

    @Test
    void aSerialIsRequired() {
        assertThatThrownBy(() -> aCylinder("  ")).isInstanceOf(InputError.class);
    }

    @Test
    void aCylinderHasChambers() {
        assertThatThrownBy(() -> aCylinder("C-9", cylinder -> cylinder.chambers(0)))
                .isInstanceOf(InputError.class);
    }

    @Test
    void aCylinderIsInstalledOrShelvedAndNotHalfOfEach() {
        assertThatThrownBy(() -> aCylinder("C-9", cylinder -> cylinder.installedAt("MOO-214")))
                .isInstanceOf(ConflictError.class);
        assertThatThrownBy(() -> aCylinder("C-9", cylinder -> cylinder.position(Position.LOCKSET)))
                .isInstanceOf(ConflictError.class);
    }

    @Test
    void aCoreKnowsItIsACore() {
        HardwareRegister register = aRegister();
        assertThat(register.cylinder("K-2001").isCore()).isTrue();
        assertThat(register.cylinder("C-1001").isCore()).isFalse();
    }

    @Test
    void installingPutsTheCylinderInTheDoor() {
        HardwareRegister register = aRegister();
        Cylinder installed = register.install("C-1001", "MOO-214", Position.LOCKSET);
        assertThat(installed.installedAt()).contains("MOO-214");
        assertThat(installed.position()).containsSame(Position.LOCKSET);
        assertThat(register.at("MOO-214", Position.LOCKSET).map(Cylinder::serial))
                .contains("C-1001");
    }

    @Test
    void aShelvedCylinderDescribesItselfThatWay() {
        HardwareRegister register = aRegister();
        assertThat(register.cylinder("C-1001").describe()).contains("on the shelf");
        register.install("C-1001", "MOO-214", Position.LOCKSET);
        assertThat(register.cylinder("C-1001").describe()).contains("MOO-214");
    }

    @Test
    void oneOpeningTakesALocksetAndADeadbolt() {
        HardwareRegister register = aRegister();
        register.install("C-1001", "MOO-218", Position.LOCKSET);
        register.install("C-1002", "MOO-218", Position.DEADBOLT);
        assertThat(serials(register.onOpening("MOO-218"))).containsExactly("C-1002", "C-1001");
    }

    @Test
    void twoCylindersCannotShareAPosition() {
        HardwareRegister register = aRegister();
        register.install("C-1001", "MOO-214", Position.LOCKSET);
        ConflictError caught = catchThrowableOfType(
                ConflictError.class, () -> register.install("C-1002", "MOO-214", Position.LOCKSET));
        assertThat(caught.facts()).containsEntry("serial", "C-1001");
    }

    @Test
    void aCylinderCannotBeInTwoDoors() {
        HardwareRegister register = aRegister();
        register.install("C-1001", "MOO-214", Position.LOCKSET);
        assertThatThrownBy(() -> register.install("C-1001", "MOO-216", Position.LOCKSET))
                .isInstanceOf(ConflictError.class);
    }

    @Test
    void anOpeningThatTakesNoCylinderRefusesOne() {
        HardwareRegister register = aRegister();
        ConflictError caught = catchThrowableOfType(
                ConflictError.class, () -> register.install("C-1001", "MOO-260", Position.LOCKSET));
        assertThat(caught.facts()).containsEntry("function", "privacy");
    }

    @Test
    void installingIntoADoorNobodyHasHeardOf() {
        HardwareRegister register = aRegister();
        assertThatThrownBy(() -> register.install("C-1001", "ZZZ-1", Position.LOCKSET))
                .isInstanceOf(NotFoundError.class);
    }

    @Test
    void pullingACylinderPutsItBackOnTheShelf() {
        HardwareRegister register = aRegister();
        register.install("C-1001", "MOO-214", Position.LOCKSET);
        register.pull("C-1001");
        assertThat(register.at("MOO-214", Position.LOCKSET)).isEmpty();
        assertThat(register.cylinder("C-1001").isInstalled()).isFalse();
    }

    @Test
    void pullingAShelvedCylinderIsRefused() {
        HardwareRegister register = aRegister();
        assertThatThrownBy(() -> register.pull("C-1001")).isInstanceOf(ConflictError.class);
    }

    @Test
    void movingACylinderFreesThePositionItLeft() {
        HardwareRegister register = aRegister();
        register.install("C-1001", "MOO-214", Position.LOCKSET);
        register.move("C-1001", "MOO-216", Position.LOCKSET);
        assertThat(register.at("MOO-214", Position.LOCKSET)).isEmpty();
        assertThat(register.at("MOO-216", Position.LOCKSET).map(Cylinder::serial))
                .contains("C-1001");
    }

    @Test
    void aCylinderJoinsTheRegisterShelved() {
        HardwareRegister register = aRegister();
        assertThatThrownBy(() -> register.add(aCylinder(
                        "C-9", cylinder -> cylinder.installedAt("MOO-214").position(Position.LOCKSET))))
                .isInstanceOf(ConflictError.class);
    }

    @Test
    void aSerialCannotBeRegisteredTwice() {
        HardwareRegister register = aRegister();
        assertThatThrownBy(() -> register.add(aCylinder("C-1001"))).isInstanceOf(ConflictError.class);
    }

    @Test
    void cylindersComeBackByBuilding() {
        HardwareRegister register = aRegister();
        register.install("C-1001", "MOO-214", Position.LOCKSET);
        register.install("C-1002", "VOS-311", Position.LOCKSET);
        assertThat(serials(register.inBuilding("MOO"))).containsExactly("C-1001");
        assertThat(serials(register.inBuilding("VOS"))).containsExactly("C-1002");
    }

    @Test
    void aGroupCanBeWrittenOnACylinder() {
        HardwareRegister register = aRegister();
        register.assignGroup("C-1001", "moore second floor offices");
        assertThat(register.cylinder("C-1001").group()).isEqualTo("moore second floor offices");
        assertThat(serials(register.inGroup("moore second floor offices"))).containsExactly("C-1001");
    }

    @Test
    void anInstalledCylinderWithNoGroupIsFlagged() {
        HardwareRegister register = aRegister();
        register.install("C-1001", "MOO-214", Position.LOCKSET);
        register.install("C-1002", "MOO-216", Position.LOCKSET);
        register.assignGroup("C-1002", "moore second floor offices");
        assertThat(serials(register.unkeyed())).containsExactly("C-1001");
    }

    @Test
    void aShelvedCylinderIsNotFlaggedAsUnkeyed() {
        assertThat(aRegister().unkeyed()).isEmpty();
    }

    @Test
    void keyedOpeningsWithNothingInThemAreReported() {
        HardwareRegister register = aRegister();
        int before = register.uncoveredOpenings().size();
        register.install("C-1001", "MOO-214", Position.LOCKSET);
        assertThat(register.uncoveredOpenings()).hasSize(before - 1);
    }

    @Test
    void aDeadboltAloneDoesNotCoverAnOpening() {
        HardwareRegister register = aRegister();
        register.install("C-1001", "MOO-214", Position.DEADBOLT);
        Set<String> numbers =
                register.uncoveredOpenings().stream().map(Opening::number).collect(Collectors.toSet());
        assertThat(numbers).contains("MOO-214");
    }

    @Test
    void theRegisterIsAContainerInSerialOrder() {
        HardwareRegister register = aRegister();
        assertThat(register.contains("C-1001")).isTrue();
        assertThat(register.contains("C-9999")).isFalse();
        List<String> serials = serials(register);
        assertThat(serials).isEqualTo(serials.stream().sorted().toList());
        assertThat(register.size()).isEqualTo(5);
    }
}
