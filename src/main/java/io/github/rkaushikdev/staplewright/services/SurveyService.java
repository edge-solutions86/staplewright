package io.github.rkaushikdev.staplewright.services;

import io.github.rkaushikdev.staplewright.ConflictError;
import io.github.rkaushikdev.staplewright.Facts;
import io.github.rkaushikdev.staplewright.NotFoundError;
import io.github.rkaushikdev.staplewright.campus.Condition;
import io.github.rkaushikdev.staplewright.campus.Deficiency;
import io.github.rkaushikdev.staplewright.campus.Survey;
import io.github.rkaushikdev.staplewright.campus.SurveyLine;
import io.github.rkaushikdev.staplewright.persistence.Session;
import io.github.rkaushikdev.staplewright.persistence.mappers.FacilitiesMappers;
import io.github.rkaushikdev.staplewright.persistence.models.SurveyLineRow;
import io.github.rkaushikdev.staplewright.persistence.models.SurveyRow;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * One walk round a building against the database, line by line, with corrections.
 *
 * <p>A survey line is typed on a tablet in a corridor, so the service takes one door at a time. A
 * building is surveyed once on a day, and a second look at the same door on the same walk is a
 * correction to the first rather than a new reading, so the service revises the line instead of
 * raising a duplicate.
 */
public final class SurveyService {

    private final Session session;

    public SurveyService(Session session) {
        this.session = session;
    }

    public Session session() {
        return session;
    }

    /**
     * Start a walk round a building.
     *
     * @throws ConflictError when the building was already walked that day
     */
    public SurveyRow start(String building, LocalDate walkedOn) {
        if (find(building, walkedOn).isPresent()) {
            throw new ConflictError(
                    "that building was already walked that day",
                    Facts.of("building", building, "walked_on", walkedOn.toString()));
        }
        SurveyRow row = new SurveyRow(building, walkedOn);
        session.add(row);
        session.flush();
        return row;
    }

    /** Write a line with nothing found and no note, or correct the one already there for that door. */
    public SurveyLineRow record(
            String building, LocalDate walkedOn, String opening, String surveyor, Condition condition) {
        return record(building, walkedOn, opening, surveyor, condition, List.of(), "");
    }

    /** Write a line with no note, or correct the one already there for that door. */
    public SurveyLineRow record(
            String building,
            LocalDate walkedOn,
            String opening,
            String surveyor,
            Condition condition,
            List<Deficiency> deficiencies) {
        return record(building, walkedOn, opening, surveyor, condition, deficiencies, "");
    }

    /**
     * Write a line, or correct the one already there for that door: the surveyor, the condition, the
     * deficiencies and the note all take the new reading.
     *
     * @throws NotFoundError when the building was not walked that day
     */
    public SurveyLineRow record(
            String building,
            LocalDate walkedOn,
            String opening,
            String surveyor,
            Condition condition,
            List<Deficiency> deficiencies,
            String note) {
        SurveyRow header = mustFind(building, walkedOn);
        SurveyLine line = new SurveyLine(opening, walkedOn, surveyor, condition, "", "", deficiencies, note);
        Optional<SurveyLineRow> held = line(header.getId(), opening);
        if (held.isEmpty()) {
            SurveyLineRow fresh = FacilitiesMappers.surveyLineToRow(line, header);
            session.add(fresh);
            session.flush();
            return fresh;
        }
        SurveyLineRow row = held.get();
        row.setSurveyor(line.surveyor());
        row.setCondition(line.condition().value());
        row.setDeficiencies(FacilitiesMappers.deficienciesToText(line.deficiencies()));
        row.setNote(line.note());
        session.flush();
        return row;
    }

    /**
     * The walk round a building on a day, every line on it.
     *
     * @throws NotFoundError when the building was not walked that day
     */
    public Survey survey(String building, LocalDate walkedOn) {
        return FacilitiesMappers.surveyFromRows(mustFind(building, walkedOn));
    }

    /** The most recent walk round a building, when anybody has walked it. */
    public Optional<Survey> latest(String building) {
        return session.first(
                        "select s from SurveyRow s where s.buildingCode = ?1 order by s.walkedOn desc",
                        SurveyRow.class,
                        building)
                .map(FacilitiesMappers::surveyFromRows);
    }

    private Optional<SurveyRow> find(String building, LocalDate walkedOn) {
        return session.first(
                "select s from SurveyRow s where s.buildingCode = ?1 and s.walkedOn = ?2",
                SurveyRow.class,
                building,
                walkedOn);
    }

    private SurveyRow mustFind(String building, LocalDate walkedOn) {
        return find(building, walkedOn)
                .orElseThrow(() -> new NotFoundError(
                        "no survey of that building on that day",
                        Facts.of("building", building, "walked_on", walkedOn.toString())));
    }

    private Optional<SurveyLineRow> line(int surveyId, String opening) {
        return session.first(
                "select l from SurveyLineRow l where l.survey.id = ?1 and l.openingNumber = ?2",
                SurveyLineRow.class,
                surveyId,
                opening);
    }
}
