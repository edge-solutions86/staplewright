package io.github.rkaushikdev.staplewright.release;

import io.github.rkaushikdev.staplewright.Facts;
import io.github.rkaushikdev.staplewright.NotFoundError;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What changed between releases, kept with the code rather than in a text document.
 *
 * <p>A release note that lives in a separate file gets written the day of the release, from
 * memory, by whoever is doing the release. A release note that lives here gets written by the
 * person making the change, because the version they are adding to is the one they are working on.
 *
 * <p>The interesting field is the migrations. A shop upgrading two versions needs to know which
 * schema migrations it is crossing, and working that out from the migration folder means reading
 * twelve files and hoping the numbering is what it looks like.
 */
public final class Releases {

    /** Every release, oldest first. */
    public static final List<Release> RELEASES = List.of(
            new Release(
                    "0.1.0",
                    LocalDate.of(2024, 11, 18),
                    "charts, symbols, bittings and the progression",
                    List.of(),
                    List.of("no database and no service, only the arithmetic")),
            new Release("0.2.0", LocalDate.of(2025, 1, 20), "the campus: buildings, openings, cylinders and coverage"),
            new Release(
                    "0.3.0",
                    LocalDate.of(2025, 3, 25),
                    "the schema, the mappers and the first migration",
                    List.of("0001"),
                    List.of()),
            new Release("0.4.0", LocalDate.of(2025, 5, 6), "the counter: requests, keys, deposits and notices"),
            new Release(
                    "0.5.0",
                    LocalDate.of(2025, 6, 18),
                    "lockouts, surveys and the audit trail",
                    List.of("0002", "0003"),
                    List.of()),
            new Release(
                    "0.6.0",
                    LocalDate.of(2025, 8, 8),
                    "the http service and the bench half of the work",
                    List.of("0004"),
                    List.of()),
            new Release(
                    "0.7.0",
                    LocalDate.of(2025, 9, 24),
                    "blank stock as a ledger, and the patent clock on restricted sections",
                    List.of("0005", "0006"),
                    List.of(
                            "key_blanks.stock is now written by the stock service only",
                            "a stocktake records the gap it found, which older counts cannot")),
            new Release(
                    "0.8.0",
                    LocalDate.of(2025, 11, 14),
                    "sealed boxes, key handovers and the annual return",
                    List.of("0007", "0008", "0009"),
                    List.of(
                            "who holds a key is the ledger plus the confirmed handovers on it",
                            "the ledger view is still correct about who signed, and is not the answer")),
            new Release(
                    "0.9.0",
                    LocalDate.of(2026, 1, 15),
                    "stamping, control keys, phasing and the self check",
                    List.of("0010", "0011", "0012"),
                    List.of(
                            "the blind code table turns a stamped number back into a symbol",
                            "control keys are their own two tables and are not issued keys")),
            new Release(
                    "1.0.0",
                    LocalDate.of(2026, 1, 20),
                    "the interface is settled",
                    List.of(),
                    List.of(
                            "the names exported from staplewright are the ones that will not move",
                            "the refusal codes in the staplewright catalogue are part of that promise",
                            "everything under staplewright persistence moves whenever the schema does")));

    /** Every release keyed by its version. */
    public static final Map<String, Release> BY_VERSION = byVersion();

    /** The latest release. */
    public static final Release CURRENT = RELEASES.get(RELEASES.size() - 1);

    private Releases() {}

    private static Map<String, Release> byVersion() {
        Map<String, Release> found = new LinkedHashMap<>();
        for (Release item : RELEASES) {
            found.put(item.version(), item);
        }
        return Collections.unmodifiableMap(found);
    }

    /** The release for a version, refusing one nobody released. */
    public static Release release(String version) {
        Release found = BY_VERSION.get(version);
        if (found == null) {
            throw new NotFoundError("no such release", Facts.of("version", version));
        }
        return found;
    }

    public static List<String> versions() {
        return RELEASES.stream().map(Release::version).toList();
    }

    /**
     * The migrations an upgrade crosses, oldest first.
     *
     * <p>Inclusive of the newer version and exclusive of the older one, which is what an upgrade
     * actually runs.
     *
     * @throws NotFoundError for a downgrade, or a version nobody released
     */
    public static List<String> migrationsBetween(String older, String newer) {
        List<Integer> first = release(older).parts();
        List<Integer> last = release(newer).parts();
        if (Release.compareParts(first, last) > 0) {
            throw new NotFoundError("that is a downgrade", Facts.of("older", older, "newer", newer));
        }
        List<String> found = new ArrayList<>();
        for (Release item : crossed(first, last)) {
            found.addAll(item.migrations());
        }
        return List.copyOf(found);
    }

    /** What somebody upgrading needs to have read, each note led by the version carrying it. */
    public static List<String> notesBetween(String older, String newer) {
        List<Integer> first = release(older).parts();
        List<Integer> last = release(newer).parts();
        List<String> found = new ArrayList<>();
        for (Release item : crossed(first, last)) {
            for (String note : item.notes()) {
                found.add(item.version() + ": " + note);
            }
        }
        return List.copyOf(found);
    }

    private static List<Release> crossed(List<Integer> first, List<Integer> last) {
        return RELEASES.stream()
                .filter(item ->
                        Release.compareParts(first, item.parts()) < 0 && Release.compareParts(item.parts(), last) <= 0)
                .toList();
    }

    /** The whole history, which is what the release command prints. */
    public static List<String> historySheet() {
        List<String> lines = new ArrayList<>();
        lines.add(RELEASES.size() + " releases, " + CURRENT.version() + " is current");
        for (Release item : RELEASES) {
            lines.add("  " + item.describe());
        }
        return List.copyOf(lines);
    }

    /** What an upgrade to the current release crosses, and what to read before starting it. */
    public static List<String> upgradeSheet(String older) {
        return upgradeSheet(older, "");
    }

    /** What an upgrade crosses and what to read before starting it. A blank target is current. */
    public static List<String> upgradeSheet(String older, String newer) {
        String target = newer.isEmpty() ? CURRENT.version() : newer;
        List<String> crossings = migrationsBetween(older, target);
        List<String> lines = new ArrayList<>();
        lines.add(older + " to " + target);
        if (crossings.isEmpty()) {
            lines.add("no migrations to run");
        } else {
            lines.add(crossings.size() + " migrations: " + String.join(", ", crossings));
        }
        List<String> notes = notesBetween(older, target);
        if (!notes.isEmpty()) {
            lines.add("read first");
            for (String note : notes) {
                lines.add("  " + note);
            }
        }
        return List.copyOf(lines);
    }

    /** Every migration any release has shipped, which the tests check against the folder. */
    public static List<String> everyMigration() {
        List<String> found = new ArrayList<>();
        for (Release item : RELEASES) {
            found.addAll(item.migrations());
        }
        return List.copyOf(found);
    }

    /** The release a version string belongs to, for the version command. */
    public static Release currentRelease(String version) {
        return release(version);
    }

    /** The current release's line, then its notes. */
    public static List<String> describeCurrent() {
        List<String> lines = new ArrayList<>();
        lines.add(CURRENT.describe());
        for (String note : CURRENT.notes()) {
            lines.add("  " + note);
        }
        return List.copyOf(lines);
    }

    /** Whether the package version and the latest release agree. */
    public static boolean matchesPackage(String version) {
        return version.equals(CURRENT.version());
    }

    /** Releases newer than a version, which is what somebody is behind by. */
    public static List<String> unreleased(String version) {
        List<Integer> held = release(version).parts();
        return RELEASES.stream()
                .filter(item -> Release.compareParts(item.parts(), held) > 0)
                .map(Release::version)
                .toList();
    }
}
