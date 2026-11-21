package io.github.rkaushikdev.staplewright.reports;

import io.github.rkaushikdev.staplewright.campus.Condition;
import io.github.rkaushikdev.staplewright.campus.Deficiency;
import io.github.rkaushikdev.staplewright.campus.Opening;
import io.github.rkaushikdev.staplewright.campus.OpeningSchedule;
import io.github.rkaushikdev.staplewright.campus.Survey;
import io.github.rkaushikdev.staplewright.campus.SurveyLine;
import io.github.rkaushikdev.staplewright.util.Tallies;
import io.github.rkaushikdev.staplewright.util.Text;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The survey report, which is the document that gets a budget line approved.
 *
 * <p>A list of four hundred doors persuades nobody. What persuades somebody is the short version:
 * eleven doors cannot be rekeyed until the frames are fixed, these are the rooms, and here is what
 * the fix costs before the rekey even starts. So the report leads with the blocking work and puts the
 * inventory at the back where it belongs.
 *
 * <p>The condition table is the other half. Nobody argues with a count of broken hardware, and a
 * count from a walk somebody signed for is worth more than a paragraph from the shop about how old
 * the buildings are.
 */
public final class SurveyPages {

    static final int OPENING_WIDTH = 12;
    static final int CONDITION_WIDTH = 10;

    /** How many buildings the worst list names unless asked for more or fewer. */
    public static final int WORST_LIMIT = 5;

    private static final Set<Deficiency> BLOCKING =
            Set.of(Deficiency.WORN_OUT, Deficiency.NO_STRIKE, Deficiency.NO_CYLINDER);

    private SurveyPages() {}

    private static String faults(SurveyLine line) {
        return line.deficiencies().stream().map(Deficiency::value).collect(Collectors.joining(", "));
    }

    /** How many doors are in each condition, every condition listed, worst first. */
    public static List<String> conditionTable(Survey survey) {
        Map<String, Integer> counts = survey.countsByCondition();
        List<String> lines = new ArrayList<>();
        lines.add(Layout.columns(List.of("condition", "doors"), CONDITION_WIDTH));
        Arrays.stream(Condition.values())
                .sorted(Comparator.comparingInt((Condition condition) -> condition.value())
                        .reversed())
                .forEach(condition -> lines.add(
                        Layout.columns(List.of(condition.label(), counts.get(condition.label())), CONDITION_WIDTH)));
        return List.copyOf(lines);
    }

    /** What was found, most common first. */
    public static List<String> deficiencyTable(Survey survey) {
        Map<String, Integer> counts = survey.countsByDeficiency();
        if (counts.isEmpty()) {
            return List.of("nothing to report on any door");
        }
        List<String> lines = new ArrayList<>();
        lines.add("deficiencies found");
        counts.forEach((name, count) -> lines.add("  " + Text.right(count, 3) + "  " + name));
        return List.copyOf(lines);
    }

    /** The doors that need other work before a rekey is worth booking, by room where the door is on record. */
    public static List<String> blockingWork(Survey survey, OpeningSchedule openings) {
        List<SurveyLine> blocked = survey.blocking();
        if (blocked.isEmpty()) {
            return List.of("nothing blocks a rekey of this building");
        }
        List<String> lines = new ArrayList<>();
        lines.add(blocked.size() + " doors need work before a rekey");
        for (SurveyLine line : blocked) {
            String where = StatisticsPages.labelOf(openings, line.opening());
            lines.add("  " + Text.left(where, OPENING_WIDTH) + faults(line));
        }
        return List.copyOf(lines);
    }

    /** Keyed doors in the building nobody looked at, which is how a survey lies. */
    public static List<String> unwalked(Survey survey, OpeningSchedule openings) {
        List<String> wanted = openings.inBuilding(survey.building()).stream()
                .filter(Opening::isKeyed)
                .map(Opening::number)
                .toList();
        List<String> missed = survey.coverage(wanted);
        if (missed.isEmpty()) {
            return List.of("every keyed door in " + survey.building() + " was walked");
        }
        List<String> lines = new ArrayList<>();
        lines.add(missed.size() + " doors not walked");
        for (String number : missed) {
            lines.add("  " + number);
        }
        return List.copyOf(lines);
    }

    /** Every line, worst door first, which is the back of the report. */
    public static List<String> inventory(Survey survey) {
        List<String> lines = new ArrayList<>();
        lines.add(Layout.columns(List.of("door", "condition", "found"), OPENING_WIDTH, CONDITION_WIDTH));
        for (SurveyLine line : survey.worstFirst()) {
            String found = faults(line);
            lines.add(Layout.columns(
                    List.of(line.opening(), line.condition().label(), found.isEmpty() ? "nothing" : found),
                    OPENING_WIDTH,
                    CONDITION_WIDTH));
        }
        return List.copyOf(lines);
    }

    /** The whole document, in the order somebody reads it. */
    public static List<String> surveyReport(Survey survey, OpeningSchedule openings) {
        String walked = String.join(", ", survey.surveyors());
        List<String> lines = new ArrayList<>();
        lines.add(survey.building() + " surveyed " + survey.walkedOn() + ", " + survey.size() + " doors");
        lines.add("walked by " + (walked.isEmpty() ? "nobody named" : walked));
        lines.add("");
        lines.addAll(blockingWork(survey, openings));
        lines.add("");
        lines.addAll(conditionTable(survey));
        lines.add("");
        lines.addAll(deficiencyTable(survey));
        lines.add("");
        lines.addAll(unwalked(survey, openings));
        lines.add("");
        lines.addAll(inventory(survey));
        return List.copyOf(lines);
    }

    /** Buildings with the most blocking doors, for the capital plan. */
    public static List<Tally> worstBuildings(Collection<Survey> surveys) {
        return worstBuildings(surveys, WORST_LIMIT);
    }

    /** The {@code limit} buildings with the most blocking doors, worst first and a tie by code. */
    public static List<Tally> worstBuildings(Collection<Survey> surveys, int limit) {
        List<Tally> counts = surveys.stream()
                .map(survey -> new Tally(survey.building(), survey.blocking().size()))
                .sorted(Tally.MOST_FIRST)
                .toList();
        return Tally.firstOf(counts, limit);
    }

    /** Which blocking fault turns up most across a set of surveys, most first and a tie by its wording. */
    public static Map<String, Integer> blockingDeficiencies(Collection<Survey> surveys) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Survey survey : surveys) {
            for (SurveyLine line : survey.blocking()) {
                for (Deficiency item : line.deficiencies()) {
                    if (BLOCKING.contains(item)) {
                        counts.merge(item.value(), 1, Integer::sum);
                    }
                }
            }
        }
        return Tallies.biggestFirst(counts);
    }
}
