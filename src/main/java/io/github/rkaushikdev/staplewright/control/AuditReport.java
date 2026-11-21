package io.github.rkaushikdev.staplewright.control;

import java.time.LocalDate;
import java.util.List;

/**
 * What one walk round a department found.
 *
 * @param on the day of the walk
 * @param department the department that put its keys on the table
 * @param counted how many different tags were held up
 * @param expected how many keys the ledger says the department's people have out
 * @param findings every line the audit cannot explain, unaccounted and wrong hands first by tag,
 *     then what turned up unexpectedly by tag
 */
public record AuditReport(LocalDate on, String department, int counted, int expected, List<AuditFinding> findings) {

    public AuditReport {
        findings = List.copyOf(findings);
    }

    /** Whether everything on the table matched the ledger. */
    public boolean isClean() {
        return findings.isEmpty();
    }

    /** The findings of one kind, in report order. */
    public List<AuditFinding> ofKind(FindingKind kind) {
        return findings.stream().filter(finding -> finding.kind() == kind).toList();
    }

    /** The keys nobody produced, which are the ones that cost money. */
    public List<AuditFinding> unaccounted() {
        return ofKind(FindingKind.UNACCOUNTED);
    }

    /** One line for the cover page. */
    public String summary() {
        return department + " " + on + ": " + counted + " counted against " + expected + " on the ledger, "
                + findings.size() + " to explain";
    }
}
