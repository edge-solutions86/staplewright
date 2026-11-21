package io.github.rkaushikdev.staplewright.persistence.models;

import io.github.rkaushikdev.staplewright.persistence.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.Arrays;
import java.util.List;
import org.hibernate.Hibernate;

/**
 * One opening as it was found on the day of a survey.
 *
 * <p>A door is surveyed once per walk, and its condition is on the shop's scale of one to four. The
 * line points at the door rather than the cylinder in it. The deficiencies are stored comma
 * separated rather than in a table of their own: they are read whole, never queried into, and
 * there are at most six of them.
 */
@Entity
@Table(
        name = "survey_lines",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uq_survey_lines_survey_id_opening_number",
                        columnNames = {"survey_id", "opening_number"}))
public class SurveyLineRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "survey_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_survey_lines_survey_id_surveys"))
    private SurveyRow survey;

    @Column(name = "opening_number", nullable = false, length = Schema.SHORT)
    private String openingNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "opening_number",
            nullable = false,
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_survey_lines_opening_number_openings"))
    private OpeningRow opening;

    @Column(nullable = false, length = Schema.MEDIUM)
    private String surveyor;

    @Column(nullable = false)
    private int condition;

    @Column(nullable = false, length = Schema.MEDIUM)
    private String manufacturer = "";

    @Column(nullable = false, length = Schema.SHORT)
    private String cylinderSerial = "";

    @Column(nullable = false, length = Schema.LONG)
    private String deficiencies = "";

    @Column(nullable = false, length = Schema.LONG)
    private String note = "";

    protected SurveyLineRow() {}

    /** A door found in a condition, listed on the survey when the survey's lines are loaded. */
    public static SurveyLineRow of(SurveyRow survey, String openingNumber, String surveyor, int condition) {
        SurveyLineRow row = new SurveyLineRow();
        row.survey = survey;
        row.openingNumber = openingNumber;
        row.surveyor = surveyor;
        row.condition = condition;
        if (Hibernate.isInitialized(survey)) {
            survey.list(row);
        }
        return row;
    }

    public Integer getId() {
        return id;
    }

    public SurveyRow getSurvey() {
        return survey;
    }

    /** The key of the survey row. */
    public Integer getSurveyId() {
        return survey.getId();
    }

    public String getOpeningNumber() {
        return openingNumber;
    }

    /** The opening, as loaded; it follows {@link #getOpeningNumber()} once the row is read again. */
    public OpeningRow getOpening() {
        return opening;
    }

    public String getSurveyor() {
        return surveyor;
    }

    public void setSurveyor(String surveyor) {
        this.surveyor = surveyor;
    }

    /** The stored value of the condition, one for good up to four for broken. */
    public int getCondition() {
        return condition;
    }

    public void setCondition(int condition) {
        this.condition = condition;
    }

    public String getManufacturer() {
        return manufacturer;
    }

    public void setManufacturer(String manufacturer) {
        this.manufacturer = manufacturer;
    }

    /** The serial read off the cylinder in the door, empty when none could be read. */
    public String getCylinderSerial() {
        return cylinderSerial;
    }

    public void setCylinderSerial(String cylinderSerial) {
        this.cylinderSerial = cylinderSerial;
    }

    /** The deficiencies found, comma separated as stored. */
    public String getDeficiencies() {
        return deficiencies;
    }

    public void setDeficiencies(String deficiencies) {
        this.deficiencies = deficiencies;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    /** The deficiencies found, in the order stored; empty when the door had none. */
    public List<String> deficiencyList() {
        if (deficiencies.isEmpty()) {
            return List.of();
        }
        return Arrays.stream(deficiencies.split(",", -1))
                .filter(part -> !part.isEmpty())
                .toList();
    }

    @Override
    public String toString() {
        return "<SurveyLineRow " + openingNumber + ">";
    }
}
