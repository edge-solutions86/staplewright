package io.github.rkaushikdev.staplewright.persistence.mappers;

import io.github.rkaushikdev.staplewright.Facts;
import io.github.rkaushikdev.staplewright.KeywayError;
import io.github.rkaushikdev.staplewright.persistence.models.BlankRow;
import io.github.rkaushikdev.staplewright.persistence.models.ChartRow;
import io.github.rkaushikdev.staplewright.persistence.models.KeywaySectionRow;
import io.github.rkaushikdev.staplewright.spec.Blank;
import io.github.rkaushikdev.staplewright.spec.DepthAndSpaceChart;
import io.github.rkaushikdev.staplewright.spec.KeywayFamily;
import io.github.rkaushikdev.staplewright.spec.KeywaySection;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Charts, keyway families and blanks, between the tables and the domain objects.
 *
 * <p>Both directions are written out by hand. A mapper that copied fields by name would be shorter,
 * and it would also mean that adding a column to a table silently adds a field to a domain object,
 * which is how a storage concern ends up in the middle of the pinning arithmetic.
 *
 * <p>The keyway family is the only awkward one, because the table is a flat list of rows with a
 * parent column and the domain object is a tree that refuses to be built out of order. So the rows
 * are sorted into depth order first and the tree is grown from the root down.
 */
public final class ReferenceMappers {

    private ReferenceMappers() {}

    /** A new chart row holding every number the chart publishes. */
    public static ChartRow chartToRow(DepthAndSpaceChart chart) {
        ChartRow row = new ChartRow(
                chart.manufacturer(),
                chart.family(),
                chart.chambers(),
                chart.depths(),
                chart.rootMils(),
                chart.incrementMils(),
                chart.firstSpaceMils(),
                chart.spaceIncrementMils(),
                chart.macs());
        row.setGaugedFromTip(chart.gaugedFromTip());
        row.setDepthToleranceMils(chart.depthToleranceMils());
        row.setNotes(chart.notes());
        return row;
    }

    /** The chart a row holds, checked as any chart is. */
    public static DepthAndSpaceChart chartFromRow(ChartRow row) {
        return new DepthAndSpaceChart(
                row.getManufacturer(),
                row.getFamily(),
                row.getChambers(),
                row.getDepths(),
                row.getRootMils(),
                row.getIncrementMils(),
                row.getFirstSpaceMils(),
                row.getSpaceIncrementMils(),
                row.getMacs(),
                row.isGaugedFromTip(),
                row.getDepthToleranceMils(),
                row.getNotes());
    }

    /** A new row for a section at the root of its family. */
    public static KeywaySectionRow sectionToRow(KeywayFamily family, KeywaySection section) {
        KeywaySectionRow row = new KeywaySectionRow(family.name(), section.code(), section.manufacturer());
        row.setRestricted(section.restricted());
        row.setPatentExpires(expiry(section).orElse(null));
        return row;
    }

    /** A new row for a section hanging under {@code parent}. */
    public static KeywaySectionRow sectionToRow(KeywayFamily family, KeywaySection section, KeywaySectionRow parent) {
        KeywaySectionRow row = sectionToRow(family, section);
        row.setParent(parent);
        return row;
    }

    /**
     * The patent date, which the section carries as the catalogue prints it.
     *
     * <p>A family written down and read back has to come out with its patent dates, or every
     * section would look as if it were restricted forever.
     */
    private static Optional<LocalDate> expiry(KeywaySection section) {
        if (section.patentExpires().isEmpty()) {
            return Optional.empty();
        }
        String written = section.patentExpires().get();
        try {
            return Optional.of(LocalDate.parse(written));
        } catch (DateTimeParseException bad) {
            KeywayError refusal = new KeywayError(
                    "that section has a patent date nobody can read",
                    Facts.of("section", section.code(), "written", written));
            refusal.initCause(bad);
            throw refusal;
        }
    }

    /**
     * One row per section, in an order that lets each parent be written before its children.
     *
     * <p>No parent is set here: the caller writes each row and points the next one at the row above
     * it, using {@link #parentsOf}. That keeps a session out of the mapper, so the mapper can be
     * checked without a database.
     */
    public static List<KeywaySectionRow> familyToRows(KeywayFamily family) {
        return depthOrder(family).stream()
                .map(code -> sectionToRow(family, family.section(code)))
                .toList();
    }

    /** Each section code against the code of the section above it, in the same order; the root has none. */
    public static Map<String, Optional<String>> parentsOf(KeywayFamily family) {
        Map<String, Optional<String>> found = new LinkedHashMap<>();
        for (String code : depthOrder(family)) {
            found.put(code, family.parentOf(code).map(KeywaySection::code));
        }
        return Collections.unmodifiableMap(found);
    }

    private static List<String> depthOrder(KeywayFamily family) {
        return family.codes().stream()
                .sorted(Comparator.comparingInt(family::depthOf))
                .toList();
    }

    /**
     * Grow the tree from the root down.
     *
     * @throws KeywayError when the rows have no root or several, when a row hangs under a row that
     *     is not among them, or when the rows do not make one tree
     */
    public static KeywayFamily familyFromRows(String name, List<KeywaySectionRow> rows) {
        Map<Integer, KeywaySectionRow> byId = new HashMap<>();
        for (KeywaySectionRow row : rows) {
            byId.put(row.getId(), row);
        }
        List<KeywaySectionRow> roots =
                rows.stream().filter(row -> row.getParentId().isEmpty()).toList();
        if (roots.size() != 1) {
            throw new KeywayError(
                    "a family has exactly one root section", Facts.of("family", name, "roots", roots.size()));
        }
        KeywayFamily family = new KeywayFamily(name, toSection(roots.get(0)));
        List<KeywaySectionRow> pending = new ArrayList<>(
                rows.stream().filter(row -> row.getParentId().isPresent()).toList());
        while (!pending.isEmpty()) {
            boolean added = false;
            for (KeywaySectionRow row : List.copyOf(pending)) {
                KeywaySectionRow parent = byId.get(row.getParentId().orElseThrow());
                if (parent == null) {
                    throw new KeywayError(
                            "a section hangs off nothing", Facts.of("code", row.getCode(), "family", name));
                }
                if (family.contains(parent.getCode())) {
                    family.add(toSection(row), parent.getCode());
                    pending.remove(row);
                    added = true;
                }
            }
            if (!added) {
                throw new KeywayError("those sections do not make one tree", Facts.of("family", name));
            }
        }
        return family;
    }

    private static KeywaySection toSection(KeywaySectionRow row) {
        return new KeywaySection(
                row.getCode(),
                row.getManufacturer(),
                row.isRestricted(),
                row.getPatentExpires().map(LocalDate::toString));
    }

    /** A new blank row cut on {@code section}, holding the stock the blank says. */
    public static BlankRow blankToRow(Blank blank, KeywaySectionRow section) {
        BlankRow row = new BlankRow(blank.partNumber(), section, blank.manufacturer(), blank.chambers());
        row.setStock(blank.stock());
        return row;
    }

    /** The blank a row holds, named by its section's code. */
    public static Blank blankFromRow(BlankRow row) {
        return new Blank(
                row.getPartNumber(),
                row.getSection().getCode(),
                row.getManufacturer(),
                row.getChambers(),
                row.getStock());
    }
}
