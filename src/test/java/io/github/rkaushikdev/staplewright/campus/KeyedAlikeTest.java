package io.github.rkaushikdev.staplewright.campus;

import static io.github.rkaushikdev.staplewright.campus.KeyedAlike.canBeKeyedAlike;
import static io.github.rkaushikdev.staplewright.campus.KeyedAlike.cylindersIn;
import static io.github.rkaushikdev.staplewright.campus.KeyedAlike.problems;
import static io.github.rkaushikdev.staplewright.support.Campus.aCampus;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.staplewright.ConflictError;
import io.github.rkaushikdev.staplewright.InputError;
import io.github.rkaushikdev.staplewright.NotFoundError;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;

class KeyedAlikeTest {

    private static KeyedAlikeSet aSet() {
        return aSet("moore cupboards");
    }

    private static KeyedAlikeSet aSet(String name, String... openings) {
        List<String> doors = openings.length == 0 ? List.of("MOO-214", "MOO-216") : List.of(openings);
        return new KeyedAlikeSet(name, doors);
    }

    /** Matching mortise cylinders in the locksets of MOO-214, MOO-216 and MOO-218. */
    private static HardwareRegister aRegister() {
        return aRegister(Map.of());
    }

    /** Cylinders in MOO-214, MOO-216 and MOO-218, each serial reshaped by its own overrides. */
    private static HardwareRegister aRegister(Map<String, UnaryOperator<Cylinder.Builder>> shapes) {
        HardwareRegister register = new HardwareRegister(aCampus());
        Map<String, String> doors = new LinkedHashMap<>();
        doors.put("C-1", "MOO-214");
        doors.put("C-2", "MOO-216");
        doors.put("C-3", "MOO-218");
        doors.forEach((serial, opening) -> {
            Cylinder plain = new Cylinder(serial, CylinderFormat.MORTISE, "LC", 6);
            UnaryOperator<Cylinder.Builder> shape = shapes.getOrDefault(serial, UnaryOperator.identity());
            register.add(shape.apply(plain.toBuilder()).build());
            register.install(serial, opening, Position.LOCKSET);
        });
        return register;
    }

    private static List<String> names(Iterable<KeyedAlikeSet> sets) {
        return StreamSupport.stream(sets.spliterator(), false)
                .map(KeyedAlikeSet::name)
                .toList();
    }

    @Test
    void aSetNeedsANameAndTwoDoors() {
        assertThatThrownBy(() -> new KeyedAlikeSet("  ", List.of("MOO-214", "MOO-216")))
                .isInstanceOf(InputError.class);
        assertThatThrownBy(() -> new KeyedAlikeSet("one", List.of("MOO-214"))).isInstanceOf(ConflictError.class);
    }

    @Test
    void aDoorCannotBeInASetTwice() {
        assertThatThrownBy(() -> new KeyedAlikeSet("twice", List.of("MOO-214", "MOO-214")))
                .isInstanceOf(ConflictError.class);
    }

    @Test
    void aSetKnowsWhatIsInIt() {
        KeyedAlikeSet alike = aSet();
        assertThat(alike.size()).isEqualTo(2);
        assertThat(alike.holds("MOO-214")).isTrue();
        assertThat(alike.holds("MOO-218")).isFalse();
        assertThat(alike.describe()).startsWith("moore cupboards: MOO-214, MOO-216");
    }

    @Test
    void theCylindersComeBackInDoorOrder() {
        List<Cylinder> found = cylindersIn(aRegister(), aSet());
        assertThat(found).extracting(Cylinder::serial).containsExactly("C-1", "C-2");
    }

    @Test
    void aMatchedSetHasNothingWrongWithIt() {
        assertThat(canBeKeyedAlike(aRegister(), aSet())).isTrue();
        assertThat(problems(aRegister(), aSet())).isEmpty();
    }

    @Test
    void aDoorWithNothingInItIsReported() {
        HardwareRegister register = aRegister();
        register.pull("C-2");
        assertThat(problems(register, aSet())).containsExactly("MOO-216 has nothing in the lockset");
    }

    @Test
    void aSetSpanningSectionsIsReported() {
        HardwareRegister register = aRegister(Map.of("C-2", cylinder -> cylinder.section("LD")));
        assertThat(problems(register, aSet())).anyMatch(line -> line.contains("keyway sections"));
    }

    @Test
    void aSetSpanningChamberCountsIsReported() {
        HardwareRegister register = aRegister(Map.of("C-2", cylinder -> cylinder.chambers(7)));
        assertThat(problems(register, aSet())).anyMatch(line -> line.contains("chamber counts"));
    }

    @Test
    void mortiseAndRimCanBeKeyedAlike() {
        HardwareRegister register = aRegister(Map.of("C-2", cylinder -> cylinder.format(CylinderFormat.RIM)));
        assertThat(canBeKeyedAlike(register, aSet())).isTrue();
    }

    @Test
    void aCoreAndAMortiseCannot() {
        HardwareRegister register = aRegister(Map.of("C-2", cylinder -> cylinder.format(CylinderFormat.SFIC)));
        assertThat(problems(register, aSet())).anyMatch(line -> line.contains("do not take one cut"));
    }

    @Test
    void aSetWithNoCylindersAtAllReportsOnlyTheEmptyDoors() {
        List<String> found = problems(new HardwareRegister(aCampus()), aSet());
        assertThat(found).hasSize(2);
        assertThat(found).allMatch(line -> line.contains("nothing in the lockset"));
    }

    @Test
    void aSetGoesOnTheRegisterOnce() {
        KeyedAlikeRegister register = new KeyedAlikeRegister();
        register.add(aSet());
        assertThatThrownBy(() -> register.add(aSet("another", "MOO-214", "MOO-218")))
                .isInstanceOf(ConflictError.class);
    }

    @Test
    void aDoorBelongsToOneSet() {
        KeyedAlikeRegister register = new KeyedAlikeRegister();
        register.add(aSet());
        assertThat(register.setHolding("MOO-214").map(KeyedAlikeSet::name)).contains("moore cupboards");
        assertThat(register.setHolding("MOO-260")).isEmpty();
    }

    @Test
    void theOtherDoorsGoUpWithIt() {
        KeyedAlikeRegister register = new KeyedAlikeRegister();
        register.add(aSet("moore cupboards", "MOO-214", "MOO-216", "MOO-218"));
        assertThat(register.openingsAlikeWith("MOO-214")).containsExactly("MOO-216", "MOO-218");
    }

    @Test
    void aDoorInNoSetTakesNothingWithIt() {
        assertThat(new KeyedAlikeRegister().openingsAlikeWith("MOO-214")).isEmpty();
    }

    @Test
    void twoSetsMergeIntoOne() {
        KeyedAlikeRegister register = new KeyedAlikeRegister();
        register.add(aSet("cupboards", "MOO-214", "MOO-216"));
        register.add(aSet("stores", "MOO-218", "MOO-110"));
        KeyedAlikeSet joined = register.merge("cupboards", "stores", "moore ground");
        assertThat(joined.size()).isEqualTo(4);
        assertThat(register.size()).isEqualTo(1);
        assertThat(register.contains("cupboards")).isFalse();
    }

    @Test
    void aSetSplitsIntoTwo() {
        KeyedAlikeRegister register = new KeyedAlikeRegister();
        register.add(aSet("moore", "MOO-214", "MOO-216", "MOO-218", "MOO-110"));
        KeyedAlikeSet taken = register.split("moore", List.of("MOO-218", "MOO-110"), "moore stores");
        assertThat(taken.size()).isEqualTo(2);
        assertThat(register.get("moore").size()).isEqualTo(2);
        assertThat(register.openingsAlikeWith("MOO-218")).containsExactly("MOO-110");
    }

    @Test
    void aSplitThatWouldLeaveOneDoorIsRefused() {
        KeyedAlikeRegister register = new KeyedAlikeRegister();
        register.add(aSet("moore", "MOO-214", "MOO-216", "MOO-218"));
        assertThatThrownBy(() -> register.split("moore", List.of("MOO-218"), "alone"))
                .isInstanceOf(ConflictError.class);
    }

    @Test
    void aSplitNamingADoorThatIsNotInTheSet() {
        KeyedAlikeRegister register = new KeyedAlikeRegister();
        register.add(aSet("moore", "MOO-214", "MOO-216", "MOO-218", "MOO-110"));
        assertThatThrownBy(() -> register.split("moore", List.of("MOO-999", "MOO-218"), "elsewhere"))
                .isInstanceOf(NotFoundError.class);
    }

    @Test
    void aSetCanBeTakenOffTheRegister() {
        KeyedAlikeRegister register = new KeyedAlikeRegister();
        register.add(aSet());
        register.remove("moore cupboards");
        assertThat(register.size()).isZero();
        assertThat(register.setHolding("MOO-214")).isEmpty();
    }

    @Test
    void aSetThatIsNotThere() {
        assertThatThrownBy(() -> new KeyedAlikeRegister().get("nothing")).isInstanceOf(NotFoundError.class);
    }

    @Test
    void theRegisterListsItsSetsInNameOrder() {
        KeyedAlikeRegister register = new KeyedAlikeRegister();
        register.add(aSet("stores", "MOO-218", "MOO-110"));
        register.add(aSet("cupboards", "MOO-214", "MOO-216"));
        assertThat(names(register)).containsExactly("cupboards", "stores");
    }
}
