package io.github.rkaushikdev.staplewright;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StoreTest {

    static Registry<String> aRegistry() {
        Registry<String> shelf = new Registry<>("cylinder", "serial");
        shelf.add("C-3", "mortise");
        shelf.add("C-1", "rim");
        shelf.add("C-2", "cam");
        return shelf;
    }

    @Test
    void thingsComeBackInTheOrderTheyArrived() {
        assertThat(aRegistry().values()).containsExactly("mortise", "rim", "cam");
        assertThat(aRegistry().keys()).containsExactly("C-3", "C-1", "C-2");
    }

    @Test
    void keyOrderIsASeparateQuestion() {
        assertThat(aRegistry().byKey()).containsExactly("rim", "cam", "mortise");
        assertThat(aRegistry().sortedKeys()).containsExactly("C-1", "C-2", "C-3");
    }

    @Test
    void aKeyCannotBeUsedTwice() {
        Registry<String> shelf = aRegistry();
        ConflictError caught = catchThrowableOfType(ConflictError.class, () -> shelf.add("C-1", "another"));
        assertThat(caught.facts()).isEqualTo(Map.of("serial", "C-1"));
        assertThat(caught.message()).contains("cylinder");
    }

    @Test
    void puttingOverSomethingKeepsItsPlaceInTheOrder() {
        Registry<String> shelf = aRegistry();
        shelf.put("C-3", "sfic");
        assertThat(shelf.keys()).containsExactly("C-3", "C-1", "C-2");
        assertThat(shelf.get("C-3")).isEqualTo("sfic");
    }

    @Test
    void puttingSomethingNewAddsItAtTheEnd() {
        Registry<String> shelf = aRegistry();
        shelf.put("C-9", "padlock");
        assertThat(shelf.keys()).last().isEqualTo("C-9");
    }

    @Test
    void aMissingKeyNamesTheNounAndTheField() {
        Registry<String> shelf = aRegistry();
        NotFoundError caught = catchThrowableOfType(NotFoundError.class, () -> shelf.get("C-9"));
        assertThat(caught.message()).isEqualTo("no such cylinder");
        assertThat(caught.facts()).isEqualTo(Map.of("serial", "C-9"));
    }

    @Test
    void findTreatsMissingAsAnAnswer() {
        Registry<String> shelf = aRegistry();
        assertThat(shelf.find("C-1")).contains("rim");
        assertThat(shelf.find("C-9")).isEmpty();
    }

    @Test
    void removingTakesItOutOfBothTheMapAndTheOrder() {
        Registry<String> shelf = aRegistry();
        assertThat(shelf.remove("C-1")).isEqualTo("rim");
        assertThat(shelf.keys()).containsExactly("C-3", "C-2");
        assertThat(shelf.has("C-1")).isFalse();
    }

    @Test
    void removingSomethingThatIsNotThere() {
        assertThat(catchThrowableOfType(NotFoundError.class, () -> aRegistry().remove("C-9")))
                .isNotNull();
    }

    @Test
    void whereKeepsTheArrivalOrder() {
        Registry<String> shelf = aRegistry();
        assertThat(shelf.where(item -> item.startsWith("m"))).containsExactly("mortise");
        assertThat(shelf.where(item -> item.contains("i"))).containsExactly("mortise", "rim");
    }

    @Test
    void whereMatchingNothingIsEmpty() {
        assertThat(aRegistry().where(item -> false)).isEmpty();
    }

    @Test
    void firstStopsAtTheEarliestMatch() {
        Registry<String> shelf = aRegistry();
        assertThat(shelf.first(item -> item.contains("i"))).contains("mortise");
        assertThat(shelf.first(item -> false)).isEmpty();
    }

    @Test
    void theRegistryIsAContainer() {
        Registry<String> shelf = aRegistry();
        assertThat(shelf.contains("C-1")).isTrue();
        assertThat(shelf.contains("C-9")).isFalse();
        assertThat(shelf.size()).isEqualTo(3);
        List<String> walked = new ArrayList<>();
        shelf.forEach(walked::add);
        assertThat(walked).containsExactly("mortise", "rim", "cam");
    }

    @Test
    void anEmptyRegistryHoldsNothing() {
        Registry<String> shelf = new Registry<>("blank");
        assertThat(shelf.size()).isZero();
        assertThat(shelf.values()).isEmpty();
        NotFoundError caught = catchThrowableOfType(NotFoundError.class, () -> shelf.get("anything"));
        assertThat(caught.facts()).isEqualTo(Map.of("id", "anything"));
    }
}
