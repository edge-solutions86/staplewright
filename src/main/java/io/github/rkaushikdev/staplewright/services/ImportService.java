package io.github.rkaushikdev.staplewright.services;

import io.github.rkaushikdev.staplewright.ConflictError;
import io.github.rkaushikdev.staplewright.Facts;
import io.github.rkaushikdev.staplewright.InputError;
import io.github.rkaushikdev.staplewright.campus.Building;
import io.github.rkaushikdev.staplewright.campus.Opening;
import io.github.rkaushikdev.staplewright.control.Affiliation;
import io.github.rkaushikdev.staplewright.control.Keyholder;
import io.github.rkaushikdev.staplewright.control.Standing;
import io.github.rkaushikdev.staplewright.persistence.Session;
import io.github.rkaushikdev.staplewright.persistence.Workspace;
import io.github.rkaushikdev.staplewright.persistence.mappers.CampusMappers;
import io.github.rkaushikdev.staplewright.persistence.mappers.ControlMappers;
import io.github.rkaushikdev.staplewright.persistence.models.BuildingRow;
import io.github.rkaushikdev.staplewright.persistence.models.KeyholderRow;
import io.github.rkaushikdev.staplewright.persistence.models.OpeningRow;
import io.github.rkaushikdev.staplewright.reports.Imports;
import io.github.rkaushikdev.staplewright.reports.Reading;
import io.github.rkaushikdev.staplewright.reports.RowProblem;
import io.github.rkaushikdev.staplewright.util.Literal;
import io.github.rkaushikdev.staplewright.util.Text;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Loading a file into the database, in the two passes it has to happen in.
 *
 * <p>The rule this exists to hold is that nothing is written until everything reads. A file of four
 * hundred openings with one bad function code used to land three hundred and eleven of them and then
 * stop, and the person holding the file had no way to know which three hundred and eleven.
 *
 * <p>So a load is a dry run and then a write, and the dry run is the same code path with the writing
 * switched off rather than a second implementation that would eventually disagree with the first.
 *
 * <p>The other rule is that a load never updates. Every row is a create, and a row whose key already
 * exists is a problem to report rather than a change to make, because a spreadsheet somebody has
 * been editing for a fortnight is not a source of truth about the rows it did not change.
 */
public final class ImportService {

    /** The columns a keyholders file has to have. Standing and an end date are optional. */
    public static final List<String> KEYHOLDER_COLUMNS = List.of("identifier", "name", "affiliation", "department");

    /** The columns a buildings file has to have. The year built is optional. */
    public static final List<String> BUILDING_COLUMNS = List.of("code", "name");

    private static final List<String> KINDS = List.of("buildings", "keyholders", "openings");

    private final Session session;
    private final Workspace space;

    /** An import service on a workspace of its own. */
    public ImportService(Session session) {
        this(session, new Workspace(session));
    }

    /** An import service sharing a workspace. */
    public ImportService(Session session, Workspace workspace) {
        this.session = session;
        this.space = workspace;
    }

    public Session session() {
        return session;
    }

    public Workspace workspace() {
        return space;
    }

    /** A file's rows, or a problem against line one for every wanted column it does not have. */
    private record Sheet(List<Map<String, String>> rows, List<RowProblem> problems) {}

    private static Sheet sheet(String text, List<String> columns) {
        Set<String> present = new HashSet<>(Imports.header(text));
        List<RowProblem> absent = columns.stream()
                .filter(name -> !present.contains(name))
                .map(name -> new RowProblem(1, name, "column is missing from the file"))
                .toList();
        if (!absent.isEmpty()) {
            return new Sheet(List.of(), absent);
        }
        return new Sheet(Imports.rows(text), List.of());
    }

    /** What a row holds under {@code column}, or nothing written there as empty text. */
    private static String value(Map<String, String> row, String column) {
        String found = row.get(column);
        return found == null ? "" : found;
    }

    /** Read a buildings file and write it. */
    public Load loadBuildings(String text) {
        return loadBuildings(text, false);
    }

    /**
     * Read a buildings file and, unless this is a dry run and when every row reads, write it. A code
     * is taken in capitals, and a building already there is a problem rather than an update.
     */
    public Load loadBuildings(String text, boolean dryRun) {
        Sheet read = sheet(text, BUILDING_COLUMNS);
        List<RowProblem> problems = new ArrayList<>(read.problems());
        List<Building> wanted = new ArrayList<>();
        int line = 1;
        for (Map<String, String> row : read.rows()) {
            line++;
            String code = Text.upper(Text.strip(value(row, "code")));
            try {
                wanted.add(new Building(code, Text.strip(value(row, "name")), built(value(row, "built"))));
            } catch (InputError | ConflictError refused) {
                problems.add(new RowProblem(line, "row", refused.getMessage()));
                continue;
            }
            if (session.get(BuildingRow.class, code).isPresent()) {
                problems.add(new RowProblem(line, "code", "that building is already there"));
            }
        }
        if (!problems.isEmpty()) {
            return new Load("buildings", 0, problems, dryRun);
        }
        if (dryRun) {
            return new Load("buildings", wanted.size(), List.of(), true);
        }
        for (Building building : wanted) {
            session.add(CampusMappers.buildingToRow(building));
        }
        session.flush();
        return new Load("buildings", wanted.size(), List.of());
    }

    /** The year a building went up, when the row gives one. */
    private static Optional<Integer> built(String text) {
        if (text.isEmpty()) {
            return Optional.empty();
        }
        long year = Text.wholeNumber(text)
                .orElseThrow(() -> new InputError("the year built is not a whole number: " + Literal.of(text)));
        return Optional.of(Math.clamp(year, Integer.MIN_VALUE, Integer.MAX_VALUE));
    }

    /** Read an openings file and write it. */
    public Load loadOpenings(String text) {
        return loadOpenings(text, false);
    }

    /**
     * Read an openings file and, unless this is a dry run and when every row reads, write it. Every
     * opening has to be on a building that is already there, and an opening already there is a
     * problem rather than an update.
     */
    public Load loadOpenings(String text, boolean dryRun) {
        Reading reading = Imports.readOpenings(text);
        List<RowProblem> problems = new ArrayList<>(reading.problems());
        int line = 1;
        for (Opening opening : reading.openings()) {
            line++;
            if (session.get(BuildingRow.class, opening.building()).isEmpty()) {
                problems.add(new RowProblem(line, "building", "no such building: " + opening.building()));
            } else if (session.get(OpeningRow.class, opening.number()).isPresent()) {
                problems.add(new RowProblem(line, "number", "that opening is already there"));
            }
        }
        if (!problems.isEmpty()) {
            return new Load("openings", 0, problems, dryRun);
        }
        if (dryRun) {
            return new Load("openings", reading.count(), List.of(), true);
        }
        for (Opening opening : reading.openings()) {
            session.add(CampusMappers.openingToRow(opening));
        }
        session.flush();
        return new Load("openings", reading.count(), List.of());
    }

    /** Read a keyholders file and write it. */
    public Load loadKeyholders(String text) {
        return loadKeyholders(text, false);
    }

    /**
     * Read a keyholders file and, unless this is a dry run and when every row reads, write it. A
     * person already there is a problem rather than an update.
     */
    public Load loadKeyholders(String text, boolean dryRun) {
        Sheet read = sheet(text, KEYHOLDER_COLUMNS);
        List<RowProblem> problems = new ArrayList<>(read.problems());
        List<Keyholder> wanted = new ArrayList<>();
        int line = 1;
        for (Map<String, String> row : read.rows()) {
            line++;
            String identifier = Text.strip(value(row, "identifier"));
            try {
                wanted.add(keyholder(row));
            } catch (InputError | ConflictError refused) {
                problems.add(new RowProblem(line, "row", refused.getMessage()));
                continue;
            }
            if (session.get(KeyholderRow.class, identifier).isPresent()) {
                problems.add(new RowProblem(line, "identifier", "that person is already there"));
            }
        }
        if (!problems.isEmpty()) {
            return new Load("keyholders", 0, problems, dryRun);
        }
        if (dryRun) {
            return new Load("keyholders", wanted.size(), List.of(), true);
        }
        for (Keyholder person : wanted) {
            session.add(ControlMappers.keyholderToRow(person));
        }
        session.flush();
        return new Load("keyholders", wanted.size(), List.of());
    }

    /** One person off a row, active unless the row says otherwise. */
    private static Keyholder keyholder(Map<String, String> row) {
        String ends = Text.strip(value(row, "ends_on"));
        String standing = value(row, "standing");
        return new Keyholder(
                Text.strip(value(row, "identifier")),
                Text.strip(value(row, "name")),
                Affiliation.fromValue(Text.strip(value(row, "affiliation"))),
                Text.strip(value(row, "department")),
                Standing.fromValue(Text.strip(standing.isEmpty() ? "active" : standing)),
                ends.isEmpty() ? Optional.empty() : Optional.of(day(ends)),
                Optional.empty());
    }

    private static LocalDate day(String text) {
        try {
            return LocalDate.parse(text);
        } catch (DateTimeParseException wrongShape) {
            InputError refusal = new InputError("that is not a day written year, month, day: " + Literal.of(text));
            refusal.initCause(wrongShape);
            throw refusal;
        }
    }

    /** One file of a named kind, written. */
    public Load load(String kind, String text) {
        return load(kind, text, false);
    }

    /**
     * One file of a named kind, which is how the command line reaches this.
     *
     * @throws InputError when nothing loads under that name
     */
    public Load load(String kind, String text, boolean dryRun) {
        return switch (kind) {
            case "buildings" -> loadBuildings(text, dryRun);
            case "openings" -> loadOpenings(text, dryRun);
            case "keyholders" -> loadKeyholders(text, dryRun);
            default -> throw new InputError("nothing loads under that name", Facts.of("kind", kind));
        };
    }

    /** Every kind of file that loads, in name order. */
    public List<String> kinds() {
        return KINDS;
    }

    /**
     * The columns one kind of file has to have.
     *
     * @throws InputError when nothing loads under that name
     */
    public List<String> columnsFor(String kind) {
        return switch (kind) {
            case "buildings" -> BUILDING_COLUMNS;
            case "openings" -> Imports.OPENING_COLUMNS;
            case "keyholders" -> KEYHOLDER_COLUMNS;
            default -> throw new InputError("nothing loads under that name", Facts.of("kind", kind));
        };
    }
}
