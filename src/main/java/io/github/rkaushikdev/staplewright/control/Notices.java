package io.github.rkaushikdev.staplewright.control;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Chasing keys that are late, in the order the manual says to chase them.
 *
 * <p>A key comes back late or it does not come back, and the difference between those two is a
 * series of letters. Reminder, then a first notice, then a second, then a hold on the record, then
 * the deposit is written off and the opening goes on the rekey list. The thresholds are policy and
 * they change, which is why they live in a {@link NoticePolicy}. What does not change is that each
 * level is sent once, which the {@link NoticeLog} remembers.
 */
public final class Notices {

    private static final Comparator<Notice> WORST_FIRST = Comparator.comparing(Notice::level, Comparator.reverseOrder())
            .thenComparing(Notice::daysLate, Comparator.reverseOrder())
            .thenComparing(Notice::tag);

    private Notices() {}

    /** The letter one late key is owed today on the shop's standing ladder, if any is. */
    public static Optional<Notice> noticeFor(IssuedKey key, LocalDate today, NoticeLog log) {
        return noticeFor(key, today, log, NoticePolicy.DEFAULT);
    }

    /**
     * The letter one late key is owed today, if any is.
     *
     * <p>Nothing is owed on a key that is not late, on one not yet late enough for the reminder, or
     * on one whose step, or a harder one, has already been sent. A key can skip straight to a late
     * step when nobody chased it earlier.
     */
    public static Optional<Notice> noticeFor(IssuedKey key, LocalDate today, NoticeLog log, NoticePolicy policy) {
        int days = key.daysLate(today);
        if (days == 0) {
            return Optional.empty();
        }
        Optional<NoticeLevel> level = policy.levelFor(days);
        if (level.isEmpty()) {
            return Optional.empty();
        }
        Optional<NoticeLevel> already = log.highestSent(key.tag());
        if (already.isPresent() && already.get().compareTo(level.get()) >= 0) {
            return Optional.empty();
        }
        return Optional.of(new Notice(key.tag(), key.keyholder(), level.get(), days, today));
    }

    /** Every letter owed today on the shop's standing ladder, worst first. */
    public static List<Notice> noticesDue(KeyLedger ledger, LocalDate today, NoticeLog log) {
        return noticesDue(ledger, today, log, NoticePolicy.DEFAULT);
    }

    /** Every letter owed today, the hardest level first, then the latest key, then by tag. */
    public static List<Notice> noticesDue(KeyLedger ledger, LocalDate today, NoticeLog log, NoticePolicy policy) {
        return ledger.overdue(today).stream()
                .flatMap(key -> noticeFor(key, today, log, policy).stream())
                .sorted(WORST_FIRST)
                .toList();
    }

    /** Keyholders whose records get a hold out of this run, each once, in identifier order. */
    public static List<String> holdsToPlace(Collection<Notice> notices) {
        return notices.stream()
                .filter(Notice::placesHold)
                .map(Notice::keyholder)
                .distinct()
                .sorted()
                .toList();
    }

    /** Tags the shop has given up on, which go to the rekey list, in tag order. */
    public static List<String> writeOffs(Collection<Notice> notices) {
        return notices.stream()
                .filter(notice -> notice.level() == NoticeLevel.WRITTEN_OFF)
                .map(Notice::tag)
                .sorted()
                .toList();
    }
}
