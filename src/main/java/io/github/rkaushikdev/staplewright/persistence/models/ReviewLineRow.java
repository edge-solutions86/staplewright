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
import java.time.LocalDate;
import java.util.Optional;
import org.hibernate.Hibernate;

/**
 * One key on one department's list. A key appears once on a review, and an answered line carries
 * the day it was answered.
 */
@Entity
@Table(
        name = "access_review_lines",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uq_access_review_lines_review_id_tag",
                        columnNames = {"review_id", "tag"}))
public class ReviewLineRow {

    private static final String UNANSWERED = "unanswered";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "review_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_access_review_lines_review_id_access_reviews"))
    private ReviewRow review;

    @Column(nullable = false, length = Schema.SHORT)
    private String tag;

    @Column(nullable = false, length = Schema.SHORT)
    private String keyholder;

    @Column(nullable = false, length = Schema.SHORT)
    private String symbol;

    @Column(nullable = false, length = Schema.SHORT)
    private String answer = UNANSWERED;

    private LocalDate answeredOn;

    @Column(nullable = false, length = Schema.LONG)
    private String note = "";

    protected ReviewLineRow() {}

    /** An unanswered line on a review, listed on the review when the review's lines are loaded. */
    public static ReviewLineRow of(ReviewRow review, String tag, String keyholder, String symbol) {
        ReviewLineRow row = new ReviewLineRow();
        row.review = review;
        row.tag = tag;
        row.keyholder = keyholder;
        row.symbol = symbol;
        if (Hibernate.isInitialized(review)) {
            review.list(row);
        }
        return row;
    }

    public Integer getId() {
        return id;
    }

    public ReviewRow getReview() {
        return review;
    }

    /** The key of the review row. */
    public Integer getReviewId() {
        return review.getId();
    }

    public String getTag() {
        return tag;
    }

    public String getKeyholder() {
        return keyholder;
    }

    public String getSymbol() {
        return symbol;
    }

    /** The stored value of the department's answer about the key. */
    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public Optional<LocalDate> getAnsweredOn() {
        return Optional.ofNullable(answeredOn);
    }

    public void setAnsweredOn(LocalDate answeredOn) {
        this.answeredOn = answeredOn;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public boolean isAnswered() {
        return !UNANSWERED.equals(answer);
    }

    @Override
    public String toString() {
        return "<ReviewLineRow " + tag + " " + answer + ">";
    }
}
