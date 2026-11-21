package io.github.rkaushikdev.staplewright.control;

import io.github.rkaushikdev.staplewright.ConflictError;
import io.github.rkaushikdev.staplewright.Facts;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * What has already been sent, so nothing goes out twice.
 *
 * <p>Each level is sent once per tag. The shop has posted the same first notice four times to one
 * graduate student and does not intend to do it again, so the log is checked before anything is
 * generated.
 */
public final class NoticeLog {

    private final Map<String, EnumSet<NoticeLevel>> sent = new HashMap<>();
    private final List<Notice> order = new ArrayList<>();

    /** Write a letter down as sent, refusing a level that already went out on that tag. */
    public Notice record(Notice notice) {
        EnumSet<NoticeLevel> levels = sent.computeIfAbsent(notice.tag(), tag -> EnumSet.noneOf(NoticeLevel.class));
        if (levels.contains(notice.level())) {
            throw new ConflictError(
                    "that notice has already gone out",
                    Facts.of("tag", notice.tag(), "level", notice.level().label()));
        }
        levels.add(notice.level());
        order.add(notice);
        return notice;
    }

    /** Every level already sent on one tag, gentlest first. */
    public Set<NoticeLevel> sentFor(String tag) {
        EnumSet<NoticeLevel> levels = sent.get(tag);
        return levels == null ? Set.of() : Collections.unmodifiableSet(EnumSet.copyOf(levels));
    }

    /** The hardest level already sent on one tag, or nothing when none has been. */
    public Optional<NoticeLevel> highestSent(String tag) {
        EnumSet<NoticeLevel> levels = sent.get(tag);
        if (levels == null || levels.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(Collections.max(levels));
    }

    /** Every letter recorded, in the order it was recorded. */
    public List<Notice> history() {
        return List.copyOf(order);
    }

    /** The key came back. The next time it goes out, start over. */
    public void clear(String tag) {
        sent.remove(tag);
    }

    public int size() {
        return order.size();
    }
}
