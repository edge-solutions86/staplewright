package io.github.rkaushikdev.staplewright.services;

import io.github.rkaushikdev.staplewright.ConflictError;
import io.github.rkaushikdev.staplewright.Facts;
import io.github.rkaushikdev.staplewright.NotFoundError;
import io.github.rkaushikdev.staplewright.persistence.Loaders;
import io.github.rkaushikdev.staplewright.persistence.Session;
import io.github.rkaushikdev.staplewright.persistence.mappers.ReferenceMappers;
import io.github.rkaushikdev.staplewright.persistence.models.KeywaySectionRow;
import io.github.rkaushikdev.staplewright.spec.Exposure;
import io.github.rkaushikdev.staplewright.spec.KeywayFamily;
import io.github.rkaushikdev.staplewright.spec.Patents;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Keyway families out of the database, and the patent clock over them.
 *
 * <p>The interesting question is {@link #atRisk}. The patents can tell you a section has gone open,
 * but have no idea whether the shop keys anything on it, and a list of expired sections nobody uses
 * is noise. Crossing it with the live systems is the whole value, and it happens here because this
 * is the only layer that can see both.
 *
 * <p>{@link #checkSection} is a separate call rather than something buried in opening a system. A
 * shop that has decided to key on a section that goes open in three years has usually decided that
 * on purpose, and a service that refused outright would just get worked around. So it refuses by
 * default and takes a flag that says somebody knows.
 */
public final class KeywayService {

    /** How far ahead the shop plans, in years. */
    public static final int PLANNING_YEARS = Patents.PLANNING_YEARS;

    private final Session session;
    private final SectionRepository sections;

    public KeywayService(Session session) {
        this.session = session;
        this.sections = new SectionRepository(session);
    }

    public Session session() {
        return session;
    }

    public SectionRepository sections() {
        return sections;
    }

    /** A family by name, refusing one nobody stored. */
    public KeywayFamily family(String name) {
        if (sections.inFamily(name).isEmpty()) {
            throw new NotFoundError("no such keyway family", Facts.of("family", name));
        }
        return Loaders.loadFamily(session, name);
    }

    public List<String> families() {
        return sections.families();
    }

    /** Write a whole family down, parent before child, refusing one already stored. */
    public List<KeywaySectionRow> store(KeywayFamily family) {
        if (!sections.inFamily(family.name()).isEmpty()) {
            throw new ConflictError("that family is already stored", Facts.of("family", family.name()));
        }
        Map<String, Optional<String>> above = ReferenceMappers.parentsOf(family);
        Map<String, KeywaySectionRow> written = new HashMap<>();
        List<KeywaySectionRow> rows = new ArrayList<>();
        for (KeywaySectionRow row : ReferenceMappers.familyToRows(family)) {
            above.get(row.getCode()).map(written::get).ifPresent(row::setParent);
            session.add(row);
            session.flush();
            written.put(row.getCode(), row);
            rows.add(row);
        }
        return List.copyOf(rows);
    }

    public List<Exposure> exposures(String familyName) {
        return Patents.exposures(family(familyName));
    }

    public List<String> openToday(String familyName, LocalDate today) {
        return Patents.openSections(family(familyName), today);
    }

    public List<String> closing(String familyName, LocalDate today) {
        return closing(familyName, today, PLANNING_YEARS);
    }

    public List<String> closing(String familyName, LocalDate today, int withinYears) {
        return Patents.closingSections(family(familyName), today, withinYears);
    }

    public Optional<Exposure> nextToGo(String familyName, LocalDate today) {
        return Patents.nextExpiry(family(familyName), today);
    }

    public List<String> report(String familyName, LocalDate today) {
        return Patents.patentReport(family(familyName), today);
    }

    /** Live systems whose top section is this one, in name order. */
    public List<String> systemsOn(String familyName, String section) {
        return session.list(
                "select s.name from SystemRow s where s.familyName = ?1 and s.topSection = ?2"
                        + " and s.archived = false order by s.name",
                String.class,
                familyName,
                section);
    }

    /** The sections live systems of a family key on, sorted. */
    public List<String> sectionsInUse(String familyName) {
        return session.list(
                "select distinct s.topSection from SystemRow s where s.familyName = ?1 and s.archived = false"
                        + " order by s.topSection",
                String.class,
                familyName);
    }

    /**
     * Sections the shop keys on that have already gone open, widest first, because a root section
     * going open exposes everything under it and is the one to move off before the leaves.
     */
    public List<RiskLine> atRisk(String familyName, LocalDate today) {
        KeywayFamily family = family(familyName);
        Set<String> inUse = new HashSet<>(sectionsInUse(familyName));
        List<RiskLine> found = new ArrayList<>();
        for (String code : Patents.migrationOrder(family, today)) {
            if (inUse.contains(code)) {
                found.add(new RiskLine(
                        familyName, code, systemsOn(familyName, code), Patents.exposureOf(family.section(code))));
            }
        }
        return List.copyOf(found);
    }

    public void checkSection(String familyName, String section, LocalDate today) {
        checkSection(familyName, section, today, PLANNING_YEARS, false);
    }

    /** Refuse a section that will not stay restricted, unless told somebody knows. */
    public void checkSection(String familyName, String section, LocalDate today, int withinYears, boolean accepted) {
        KeywayFamily family = family(familyName);
        family.section(section);
        if (Patents.sectionIsSafe(family, section, today, withinYears) || accepted) {
            return;
        }
        Exposure item = Patents.exposureOf(family.section(section));
        throw new ConflictError(
                "that section will not stay restricted long enough to key on",
                Facts.of(
                        "family",
                        familyName,
                        "section",
                        section,
                        "expires",
                        item.expiresOn().map(LocalDate::toString).orElse("never restricted"),
                        "horizon",
                        withinYears));
    }

    /** Every family the shop holds, worst first, on one page. */
    public List<String> riskReport(LocalDate today) {
        List<String> lines = new ArrayList<>();
        lines.add("keyway patent position at " + today);
        List<RiskLine> found = new ArrayList<>();
        for (String name : families()) {
            found.addAll(atRisk(name, today));
        }
        if (found.isEmpty()) {
            lines.add("nothing the shop keys on has gone open");
            return List.copyOf(lines);
        }
        lines.add(found.size() + " sections in use have gone open");
        for (RiskLine line : found) {
            lines.add("  " + line.describe(today));
        }
        return List.copyOf(lines);
    }
}
