package io.github.rkaushikdev.staplewright;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LifecycleTest {

    enum Where implements Valued {
        OPEN("open"),
        SHUT("shut"),
        GONE("gone");

        private final String value;

        Where(String value) {
            this.value = value;
        }

        @Override
        public String value() {
            return value;
        }

        @Override
        public String toString() {
            return value;
        }
    }

    @Test
    void aStateThatIsAllowedPassesQuietly() {
        assertThatCode(() -> Lifecycle.requireState(Where.OPEN, Set.of(Where.OPEN, Where.SHUT), "no"))
                .doesNotThrowAnyException();
    }

    @Test
    void aStateThatIsNotAllowedRefuses() {
        ConflictError caught = catchThrowableOfType(
                ConflictError.class,
                () -> Lifecycle.requireState(
                        Where.GONE, Set.of(Where.OPEN), "the thing is not open", Facts.of("number", "W-1")));
        assertThat(caught.message()).isEqualTo("the thing is not open");
    }

    @Test
    void theStateAlwaysReachesTheFacts() {
        ConflictError caught = catchThrowableOfType(
                ConflictError.class, () -> Lifecycle.requireState(Where.GONE, Set.of(Where.OPEN), "no"));
        assertThat(caught.facts()).containsEntry("state", "gone");
    }

    @Test
    void theAllowedStatesReachTheFactsToo() {
        ConflictError caught = catchThrowableOfType(
                ConflictError.class, () -> Lifecycle.requireState(Where.GONE, Set.of(Where.OPEN), "no"));
        assertThat(caught.facts()).containsEntry("allowed", List.of("open"));
    }

    @Test
    void theCallersOwnFactsComeThrough() {
        ConflictError caught = catchThrowableOfType(
                ConflictError.class,
                () -> Lifecycle.requireState(
                        Where.GONE, Set.of(Where.OPEN), "no", Facts.of("number", "W-1041", "serial", "C-1")));
        assertThat(caught.facts()).containsEntry("number", "W-1041").containsEntry("serial", "C-1");
    }

    @Test
    void aListOfAllowedStatesWorksAsWellAsASet() {
        assertThatCode(() -> Lifecycle.requireState(Where.SHUT, List.of(Where.OPEN, Where.SHUT), "no"))
                .doesNotThrowAnyException();
    }

    @Test
    void anEmptySetOfAllowedStatesRefusesEverything() {
        assertThatThrownBy(() -> Lifecycle.requireState(Where.OPEN, List.of(), "nothing is allowed"))
                .isInstanceOf(ConflictError.class);
    }

    @Test
    void plainStringsWorkTheSameWay() {
        Lifecycle.requireState("drafted", Set.of("drafted", "scheduled"), "no");
        assertThatThrownBy(() -> Lifecycle.requireState("done", Set.of("drafted"), "no"))
                .isInstanceOf(ConflictError.class);
    }

    @Test
    void aConditionThatHoldsPassesQuietly() {
        assertThatCode(() -> Lifecycle.require(true, "no")).doesNotThrowAnyException();
    }

    @Test
    void aConditionThatDoesNotHoldRefuses() {
        ConflictError caught = catchThrowableOfType(
                ConflictError.class, () -> Lifecycle.require(false, "that cannot be done", Facts.of("tag", "41207")));
        assertThat(caught.facts()).isEqualTo(Map.of("tag", "41207"));
    }

    @Test
    void aStatePrintsAsItsValue() {
        assertThat(Lifecycle.stateOf(Where.OPEN)).isEqualTo("open");
        assertThat(Lifecycle.stateOf("drafted")).isEqualTo("drafted");
        assertThat(Lifecycle.stateOf(4)).isEqualTo("4");
    }
}
