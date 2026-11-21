package io.github.rkaushikdev.staplewright.control;

import static io.github.rkaushikdev.staplewright.support.People.TODAY;
import static io.github.rkaushikdev.staplewright.support.People.aDirectory;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.staplewright.ConflictError;
import io.github.rkaushikdev.staplewright.InputError;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class NoticesTest {

    private static final LocalDate DUE = LocalDate.of(2025, 3, 20);

    private static KeyLedger aLedger() {
        KeyLedger ledger = new KeyLedger(aDirectory());
        ledger.issue("41207", "AA1", "kmaki", TODAY, DUE);
        ledger.issue("41208", "AA1", "dsalo", TODAY, DUE);
        ledger.issue("41209", "AA", "rvirta", TODAY);
        return ledger;
    }

    private static LocalDate daysAfter(int days) {
        return DUE.plusDays(days);
    }

    /** The letter a key is owed, for a test that knows one is owed. */
    private static Notice owed(KeyLedger ledger, String tag, LocalDate today, NoticeLog log) {
        return Notices.noticeFor(ledger.key(tag), today, log).orElseThrow();
    }

    private static List<String> tags(List<Notice> notices) {
        return notices.stream().map(Notice::tag).toList();
    }

    @Test
    void theStepsHaveToGetLater() {
        assertThatThrownBy(() -> new NoticePolicy(30, 14, 30, 60, 120)).isInstanceOf(ConflictError.class);
    }

    @Test
    void twoStepsCannotLandOnTheSameDay() {
        assertThatThrownBy(() -> new NoticePolicy(3, 14, 14, 60, 120)).isInstanceOf(ConflictError.class);
    }

    @Test
    void aNegativeThresholdIsRefused() {
        assertThatThrownBy(() -> new NoticePolicy(-1, 14, 30, 60, 120)).isInstanceOf(InputError.class);
    }

    @ParameterizedTest
    @CsvSource({"0,", "2,", "3, REMINDER", "13, REMINDER", "14, FIRST", "30, SECOND", "60, HOLD", "400, WRITTEN_OFF"})
    void thePolicyReadsDaysIntoAStep(int days, NoticeLevel level) {
        assertThat(new NoticePolicy().levelFor(days)).isEqualTo(Optional.ofNullable(level));
    }

    @Test
    void aKeyThatIsNotLateOwesNothing() {
        KeyLedger ledger = aLedger();
        assertThat(Notices.noticeFor(ledger.key("41207"), DUE, new NoticeLog())).isEmpty();
    }

    @Test
    void aKeyAFewDaysLateOwesAReminder() {
        KeyLedger ledger = aLedger();
        Optional<Notice> notice = Notices.noticeFor(ledger.key("41207"), daysAfter(4), new NoticeLog());
        assertThat(notice).isPresent();
        assertThat(notice.get().level()).isSameAs(NoticeLevel.REMINDER);
        assertThat(notice.get().daysLate()).isEqualTo(4);
        assertThat(notice.get().describe()).contains("reminder");
    }

    @Test
    void aKeyWithNoDueDateOwesNothingEver() {
        KeyLedger ledger = aLedger();
        assertThat(Notices.noticeFor(ledger.key("41209"), LocalDate.of(2030, 1, 1), new NoticeLog()))
                .isEmpty();
    }

    @Test
    void theSameStepDoesNotGoOutTwice() {
        KeyLedger ledger = aLedger();
        NoticeLog log = new NoticeLog();
        Notice first = owed(ledger, "41207", daysAfter(4), log);
        log.record(first);
        assertThat(Notices.noticeFor(ledger.key("41207"), daysAfter(6), log)).isEmpty();
    }

    @Test
    void theNextStepDoesGoOut() {
        KeyLedger ledger = aLedger();
        NoticeLog log = new NoticeLog();
        log.record(owed(ledger, "41207", daysAfter(4), log));
        Notice later = owed(ledger, "41207", daysAfter(20), log);
        assertThat(later.level()).isSameAs(NoticeLevel.FIRST);
    }

    @Test
    void skippingStraightToALateStepIsAllowed() {
        KeyLedger ledger = aLedger();
        Notice notice = owed(ledger, "41207", daysAfter(70), new NoticeLog());
        assertThat(notice.level()).isSameAs(NoticeLevel.HOLD);
    }

    @Test
    void recordingTheSameNoticeTwiceIsRefused() {
        KeyLedger ledger = aLedger();
        NoticeLog log = new NoticeLog();
        Notice notice = owed(ledger, "41207", daysAfter(4), log);
        log.record(notice);
        assertThatThrownBy(() -> log.record(notice)).isInstanceOf(ConflictError.class);
    }

    @Test
    void theLogRemembersWhatWentOut() {
        KeyLedger ledger = aLedger();
        NoticeLog log = new NoticeLog();
        log.record(owed(ledger, "41207", daysAfter(4), log));
        assertThat(log.sentFor("41207")).isEqualTo(Set.of(NoticeLevel.REMINDER));
        assertThat(log.highestSent("41207")).contains(NoticeLevel.REMINDER);
        assertThat(log.highestSent("41208")).isEmpty();
        assertThat(log.size()).isEqualTo(1);
    }

    @Test
    void clearingATagStartsTheChaseOver() {
        KeyLedger ledger = aLedger();
        NoticeLog log = new NoticeLog();
        log.record(owed(ledger, "41207", daysAfter(4), log));
        log.clear("41207");
        assertThat(log.highestSent("41207")).isEmpty();
        assertThat(Notices.noticeFor(ledger.key("41207"), daysAfter(6), log)).isPresent();
    }

    @Test
    void aRunCoversEveryLateKey() {
        KeyLedger ledger = aLedger();
        List<Notice> due = Notices.noticesDue(ledger, daysAfter(4), new NoticeLog());
        assertThat(due.stream().map(Notice::tag).collect(Collectors.toSet())).isEqualTo(Set.of("41207", "41208"));
    }

    @Test
    void aRunLeavesOutKeysThatAreNotLate() {
        assertThat(Notices.noticesDue(aLedger(), DUE, new NoticeLog())).isEmpty();
    }

    @Test
    void theWorstLetterComesFirst() {
        KeyLedger ledger = aLedger();
        NoticeLog log = new NoticeLog();
        log.record(owed(ledger, "41208", daysAfter(4), log));
        log.record(owed(ledger, "41208", daysAfter(20), log));
        List<Notice> due = Notices.noticesDue(ledger, daysAfter(40), log);
        assertThat(tags(due)).containsExactly("41207", "41208");
        assertThat(due.get(0).level()).isSameAs(NoticeLevel.SECOND);
    }

    @Test
    void aReturnedKeyDropsOutOfTheRun() {
        KeyLedger ledger = aLedger();
        ledger.takeBack("41207", daysAfter(1));
        List<Notice> due = Notices.noticesDue(ledger, daysAfter(40), new NoticeLog());
        assertThat(tags(due)).containsExactly("41208");
    }

    @Test
    void aHoldIsPlacedFromTheHoldStepUp() {
        KeyLedger ledger = aLedger();
        List<Notice> due = Notices.noticesDue(ledger, daysAfter(70), new NoticeLog());
        assertThat(Notices.holdsToPlace(due)).containsExactly("dsalo", "kmaki");
    }

    @Test
    void noHoldBeforeTheHoldStep() {
        KeyLedger ledger = aLedger();
        assertThat(Notices.holdsToPlace(Notices.noticesDue(ledger, daysAfter(20), new NoticeLog())))
                .isEmpty();
    }

    @Test
    void aWrittenOffKeyGoesOnTheRekeyList() {
        KeyLedger ledger = aLedger();
        List<Notice> due = Notices.noticesDue(ledger, daysAfter(200), new NoticeLog());
        assertThat(Notices.writeOffs(due)).containsExactly("41207", "41208");
        assertThat(Notices.holdsToPlace(due)).containsExactly("dsalo", "kmaki");
    }

    @Test
    void nothingIsWrittenOffEarly() {
        KeyLedger ledger = aLedger();
        assertThat(Notices.writeOffs(Notices.noticesDue(ledger, daysAfter(70), new NoticeLog())))
                .isEmpty();
    }

    @Test
    void aShopThatChasesHarderCanSaySo() {
        KeyLedger ledger = aLedger();
        NoticePolicy policy = new NoticePolicy(1, 2, 3, 4, 120);
        List<Notice> due = Notices.noticesDue(ledger, daysAfter(5), new NoticeLog(), policy);
        assertThat(due).allMatch(notice -> notice.level() == NoticeLevel.HOLD);
    }
}
