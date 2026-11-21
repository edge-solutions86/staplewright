package io.github.rkaushikdev.staplewright.cli;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.staplewright.support.Cli;
import io.github.rkaushikdev.staplewright.support.DbCampus;
import io.github.rkaushikdev.staplewright.support.TestDatabase;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CliControlTest {

    private static final String ISSUED = "2025-01-06";
    private static final String LATER = "2025-12-01";
    private static final String CUT = "3572461";

    @BeforeEach
    void shop() {
        TestDatabase.fresh().inSession(DbCampus::build);
    }

    private static Cli.Result call(String... args) {
        return Cli.onTestDatabase(args);
    }

    private static Cli.Result aKey() {
        return aKey("CK-1", "A");
    }

    private static Cli.Result aKey(String code, String section) {
        return call("control", "cut", code, section, CUT, "--on", ISSUED);
    }

    private static Cli.Result aCore() {
        return aCore("K-1001", "CK-1", "mortise");
    }

    private static Cli.Result aCore(String serial, String code, String housing) {
        return call("control", "core", serial, code, "--housing", housing);
    }

    @Test
    void cuttingAControlKeySaysSo() {
        assertThat(aKey().output().strip()).isEqualTo("CK-1 cut on A");
    }

    @Test
    void aCodeIsNotReused() {
        aKey();
        assertThat(aKey().exitCode()).isNotZero();
    }

    @Test
    void aBittingNobodyCanReadIsRefused() {
        assertThat(call("control", "cut", "CK-1", "A", "not a cut", "--on", ISSUED)
                        .exitCode())
                .isNotZero();
    }

    @Test
    void aFormatNobodyMakesIsRejectedAsUnreadable() {
        assertThat(call("control", "cut", "CK-1", "A", CUT, "--format", "medium", "--on", ISSUED)
                        .exitCode())
                .isNotZero();
    }

    @Test
    void handingAKeyOverSaysWho() {
        aKey();
        assertThat(call("control", "hand-to", "CK-1", "phamm").output().strip()).isEqualTo("CK-1 is with phamm");
    }

    @Test
    void withdrawingAKeySaysSo() {
        aKey();
        assertThat(call("control", "withdraw", "CK-1", "--on", LATER).output().strip())
                .isEqualTo("CK-1 withdrawn");
    }

    @Test
    void aKeyWithCoresCannotBeWithdrawn() {
        aKey();
        aCore();
        assertThat(call("control", "withdraw", "CK-1", "--on", LATER).exitCode())
                .isNotZero();
    }

    @Test
    void aCoreGoesOnTheRegister() {
        aKey();
        assertThat(aCore().output().strip()).isEqualTo("K-1001 on the register, pulled by CK-1");
    }

    @Test
    void aCoreNeedsAControlKeyThatExists() {
        assertThat(aCore().exitCode()).isNotZero();
    }

    @Test
    void aCoreGoesIntoADoor() {
        aKey();
        aCore();
        assertThat(call("control", "install", "K-1001", "MOO-101").output().strip())
                .isEqualTo("K-1001 is in MOO-101");
    }

    @Test
    void aHousingOnlyTakesOneCore() {
        aKey();
        aCore("K-1001", "CK-1", "mortise");
        aCore("K-1002", "CK-1", "mortise");
        call("control", "install", "K-1001", "MOO-101");
        assertThat(call("control", "install", "K-1002", "MOO-101").exitCode()).isNotZero();
    }

    @Test
    void pullingACoreTakesItOffTheWall() {
        aKey();
        aCore();
        call("control", "install", "K-1001", "MOO-101");
        assertThat(call("control", "pull", "K-1001").output().strip()).isEqualTo("K-1001 is off the wall");
    }

    @Test
    void aCoreAlreadyOffTheWallCannotBePulled() {
        aKey();
        aCore();
        assertThat(call("control", "pull", "K-1001").exitCode()).isNotZero();
    }

    @Test
    void anEmptyRegisterSaysSo() {
        assertThat(call("control", "keys").output()).contains("no control keys on the register");
    }

    @Test
    void theKeysAreListedWithWhoHasThem() {
        aKey();
        call("control", "hand-to", "CK-1", "phamm");
        assertThat(call("control", "keys").output()).contains("held by phamm");
    }

    @Test
    void noControlKeyOutSaysSo() {
        assertThat(call("control", "holders").output()).contains("no control key is out");
    }

    @Test
    void theHoldersAreCounted() {
        aKey();
        call("control", "hand-to", "CK-1", "phamm");
        List<String> lines = call("control", "holders").lines();
        assertThat(lines.get(0)).isEqualTo("1 control keys are out");
    }

    @Test
    void anEmptyCoreRegisterSaysSo() {
        assertThat(call("control", "cores").output()).contains("no cores on the register");
    }

    @Test
    void theCoresListSaysWhereEachOneIs() {
        aKey();
        aCore();
        call("control", "install", "K-1001", "MOO-101");
        assertThat(call("control", "cores").output()).contains("MOO-101");
    }

    @Test
    void aShelvedCoreSaysSo() {
        aKey();
        aCore();
        assertThat(call("control", "cores").output()).contains("on the shelf");
    }

    @Test
    void theCoresCanBeNarrowedToOneKey() {
        aKey();
        aKey("CK-2", "B");
        aCore("K-1001", "CK-1", "mortise");
        aCore("K-1002", "CK-2", "mortise");
        String output = call("control", "cores", "CK-2").output();
        assertThat(output).contains("K-1002").doesNotContain("K-1001");
    }

    @Test
    void oneControlSectionCrossesNothing() {
        aKey();
        aCore("K-1001", "CK-1", "mortise");
        aCore("K-1002", "CK-1", "mortise");
        assertThat(call("control", "crossings").output()).contains("no core can be moved across a control key");
    }

    @Test
    void twoControlKeysOnOneHousingKindCross() {
        aKey();
        aKey("CK-2", "B");
        aCore("K-1001", "CK-1", "mortise");
        aCore("K-1002", "CK-2", "mortise");
        String output = call("control", "crossings").output();
        assertThat(output).contains("more than one control section").contains("K-1001 and K-1002");
    }

    @Test
    void differentHousingsDoNotCross() {
        aKey();
        aKey("CK-2", "B");
        aCore("K-1001", "CK-1", "mortise");
        aCore("K-1002", "CK-2", "rim");
        assertThat(call("control", "crossings").output()).contains("no core can be moved");
    }
}
