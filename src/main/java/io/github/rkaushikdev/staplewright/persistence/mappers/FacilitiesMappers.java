package io.github.rkaushikdev.staplewright.persistence.mappers;

import io.github.rkaushikdev.staplewright.Facts;
import io.github.rkaushikdev.staplewright.InputError;
import io.github.rkaushikdev.staplewright.campus.CallState;
import io.github.rkaushikdev.staplewright.campus.Condition;
import io.github.rkaushikdev.staplewright.campus.Deficiency;
import io.github.rkaushikdev.staplewright.campus.Identification;
import io.github.rkaushikdev.staplewright.campus.Lockout;
import io.github.rkaushikdev.staplewright.campus.LockoutLog;
import io.github.rkaushikdev.staplewright.campus.Survey;
import io.github.rkaushikdev.staplewright.campus.SurveyLine;
import io.github.rkaushikdev.staplewright.persistence.models.LockoutRow;
import io.github.rkaushikdev.staplewright.persistence.models.SurveyLineRow;
import io.github.rkaushikdev.staplewright.persistence.models.SurveyRow;
import io.github.rkaushikdev.staplewright.util.Text;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Lockout calls and survey lines, between the tables and the domain objects.
 *
 * <p>The deficiencies are the only thing worth explaining. They go into one column as their own
 * text, comma separated, and come back as choices. A word the code has never heard of is dropped
 * rather than refused, because a survey from 2014 carries words this version of the software does
 * not know and losing one of them is better than losing the whole line.
 *
 * <p>That is a deliberate asymmetry: everywhere else in this library an unknown value is a refusal.
 * Here the row is evidence of something a person saw, and evidence does not get thrown away for
 * being unfashionable. An identification nobody recognises reads as no identification, for the same reason.
 */
public final class FacilitiesMappers {

    private FacilitiesMappers() {}

    /** A survey's header row and one row per line, the lines already listed on the header. */
    public record SurveyRows(SurveyRow survey, List<SurveyLineRow> lines) {

        public SurveyRows {
            lines = List.copyOf(lines);
        }
    }

    private static Identification identification(String value) {
        return Identification.find(value).orElse(Identification.NONE);
    }

    /** A new lockout row, its state and identification stored as their values. */
    public static LockoutRow lockoutToRow(Lockout call) {
        LockoutRow row = new LockoutRow(call.number(), call.opening(), call.keyholder(), call.calledAt());
        row.setState(call.state().value());
        row.setAttendedAt(call.attendedAt().orElse(null));
        row.setClosedAt(call.closedAt().orElse(null));
        row.setTechnician(call.technician());
        row.setIdentifiedBy(call.identifiedBy().value());
        row.setNote(call.note());
        return row;
    }

    /**
     * The lockout call a row holds.
     *
     * @throws InputError for a state nobody has heard of
     */
    public static Lockout lockoutFromRow(LockoutRow row) {
        CallState state = CallState.find(row.getState())
                .orElseThrow(() -> new InputError("no such lockout state", Facts.of("state", row.getState())));
        return new Lockout(
                row.getNumber(),
                row.getOpeningNumber(),
                row.getKeyholder(),
                row.getCalledAt(),
                state,
                row.getAttendedAt(),
                row.getClosedAt(),
                row.getTechnician(),
                identification(row.getIdentifiedBy()),
                row.getNote());
    }

    /** Every call back in the order it came in, whatever state it reached. */
    public static LockoutLog logFromRows(List<LockoutRow> rows) {
        LockoutLog log = new LockoutLog();
        rows.stream()
                .sorted(Comparator.comparing(LockoutRow::getCalledAt).thenComparing(LockoutRow::getNumber))
                .forEach(row -> log.load(lockoutFromRow(row)));
        return log;
    }

    /** The deficiencies as their own words, comma separated, in the order given. */
    public static String deficienciesToText(List<Deficiency> items) {
        return items.stream().map(Deficiency::value).collect(Collectors.joining(","));
    }

    /** Known deficiencies, in the order written, each once, with anything unknown dropped. */
    public static List<Deficiency> deficienciesFromText(String text) {
        List<Deficiency> found = new ArrayList<>();
        for (String part : text.split(",", -1)) {
            Optional<Deficiency> item = Deficiency.find(Text.strip(part));
            if (item.isPresent() && !found.contains(item.get())) {
                found.add(item.get());
            }
        }
        return List.copyOf(found);
    }

    /** A new line row on {@code survey}, its condition stored as its number. */
    public static SurveyLineRow surveyLineToRow(SurveyLine line, SurveyRow survey) {
        SurveyLineRow row = SurveyLineRow.of(
                survey, line.opening(), line.surveyor(), line.condition().value());
        row.setManufacturer(line.manufacturer());
        row.setCylinderSerial(line.cylinderSerial());
        row.setDeficiencies(deficienciesToText(line.deficiencies()));
        row.setNote(line.note());
        return row;
    }

    /**
     * The survey line a row holds, dated the day the survey was walked.
     *
     * @throws InputError for a condition number nobody has heard of
     */
    public static SurveyLine surveyLineFromRow(SurveyLineRow row, LocalDate walkedOn) {
        return new SurveyLine(
                row.getOpeningNumber(),
                walkedOn,
                row.getSurveyor(),
                Condition.fromValue(row.getCondition()),
                row.getManufacturer(),
                row.getCylinderSerial(),
                deficienciesFromText(row.getDeficiencies()),
                row.getNote());
    }

    /** A header and its lines. The caller writes the header and then each line. */
    public static SurveyRows surveyToRows(Survey survey) {
        SurveyRow header = new SurveyRow(survey.building(), survey.walkedOn());
        List<SurveyLineRow> lines = survey.lines().stream()
                .map(line -> surveyLineToRow(line, header))
                .toList();
        return new SurveyRows(header, lines);
    }

    /** The survey a header row holds, its lines recorded in the order loaded. */
    public static Survey surveyFromRows(SurveyRow row) {
        Survey survey = new Survey(row.getBuildingCode(), row.getWalkedOn());
        for (SurveyLineRow line : row.getLines()) {
            survey.record(surveyLineFromRow(line, row.getWalkedOn()));
        }
        return survey;
    }
}
