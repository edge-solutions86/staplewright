package io.github.rkaushikdev.staplewright.reports;

import io.github.rkaushikdev.staplewright.util.Text;
import java.lang.reflect.Method;

/**
 * One report on the index: the report module it lives in, the name it is printed under, who asks for
 * it, and the question it answers.
 *
 * @param module the report module, as {@link ReportIndex#MODULES} names it: {@code survey}
 * @param name the name the page is printed under: {@code survey_report}
 * @param askedBy the kind of person who asks for it: {@code management}
 * @param answers the question it answers, in that person's words
 */
public record Page(String module, String name, String askedBy, String answers) {

    /** Where the page lives, which is how it is found again: {@code staplewright.reports.survey.survey_report}. */
    public String path() {
        return "staplewright.reports." + module + "." + name;
    }

    /** One line for the index: the name, who asks for it, and what it answers, in columns. */
    public String describe() {
        return Text.left(name, 24) + Text.left(askedBy, 12) + answers;
    }

    /**
     * The report method itself, which is what makes the index a test. Where a page has several
     * forms, this is the one that takes the most.
     *
     * @throws io.github.rkaushikdev.staplewright.NotFoundError when the module or the method is not
     *     there
     */
    public Method resolve() {
        return ReportIndex.resolve(this);
    }
}
