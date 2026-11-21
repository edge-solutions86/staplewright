package io.github.rkaushikdev.staplewright.control;

/**
 * One line the audit cannot explain.
 *
 * @param kind which of the four mismatches this is
 * @param tag the key it is about
 * @param expected who the ledger says has it, empty when the ledger says nobody
 * @param seen who held it up, empty when nobody did
 */
public record AuditFinding(FindingKind kind, String tag, String expected, String seen) {

    /** A key out to {@code expected} that nobody produced. */
    public static AuditFinding unaccounted(String tag, String expected) {
        return new AuditFinding(FindingKind.UNACCOUNTED, tag, expected, "");
    }

    /** A tag {@code seen} held up that the ledger never issued. */
    public static AuditFinding stray(String tag, String seen) {
        return new AuditFinding(FindingKind.STRAY, tag, "", seen);
    }

    /** A key the ledger closed that {@code seen} still has. */
    public static AuditFinding closed(String tag, String seen) {
        return new AuditFinding(FindingKind.CLOSED, tag, "", seen);
    }

    /** A key out to {@code expected} that {@code seen} has. */
    public static AuditFinding wrongHolder(String tag, String expected, String seen) {
        return new AuditFinding(FindingKind.WRONG_HOLDER, tag, expected, seen);
    }

    /** The finding as a sentence for the audit sheet. */
    public String describe() {
        return switch (kind) {
            case UNACCOUNTED -> tag + " is out to " + expected + " and nobody produced it";
            case STRAY -> tag + " turned up with " + seen + " and is not in the ledger";
            case CLOSED -> tag + " was closed on paper and " + seen + " still has it";
            case WRONG_HOLDER -> tag + " is out to " + expected + " and " + seen + " has it";
        };
    }
}
