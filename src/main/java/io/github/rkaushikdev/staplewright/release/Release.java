package io.github.rkaushikdev.staplewright.release;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * One version, what it added, and what it needs on the way in.
 *
 * @param version the version, as {@code major.minor.patch}
 * @param releasedOn the day it shipped
 * @param headline what it was for, in a phrase
 * @param migrations the schema migrations it ships, by their four digit number
 * @param notes what somebody upgrading across it has to have read
 */
public record Release(
        String version, LocalDate releasedOn, String headline, List<String> migrations, List<String> notes) {

    /** Releases by version number, oldest first. */
    public static final Comparator<Release> BY_VERSION = Comparator.comparing(Release::parts, Release::compareParts);

    public Release {
        migrations = List.copyOf(migrations);
        notes = List.copyOf(notes);
    }

    /** A release that ships no migrations and carries no notes. */
    public Release(String version, LocalDate releasedOn, String headline) {
        this(version, releasedOn, headline, List.of(), List.of());
    }

    /** The major, minor and patch numbers. */
    public List<Integer> parts() {
        String[] pieces = version.split("\\.", -1);
        return List.of(Integer.parseInt(pieces[0]), Integer.parseInt(pieces[1]), Integer.parseInt(pieces[2]));
    }

    /** The version, the day and the headline, on one line. */
    public String describe() {
        return String.format(Locale.ROOT, "%-8s%s  %s", version, releasedOn, headline);
    }

    /** Version numbers compared part by part, so 0.10.0 comes after 0.9.0. */
    public static int compareParts(List<Integer> first, List<Integer> second) {
        for (int index = 0; index < Math.min(first.size(), second.size()); index++) {
            int compared = Integer.compare(first.get(index), second.get(index));
            if (compared != 0) {
                return compared;
            }
        }
        return Integer.compare(first.size(), second.size());
    }
}
