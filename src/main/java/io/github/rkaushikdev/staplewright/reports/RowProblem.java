package io.github.rkaushikdev.staplewright.reports;

/**
 * One row of an imported file that cannot be read, and the line it is on.
 *
 * @param line the line of the file, counting the header as line one
 * @param column the column at fault, or {@code row} when the whole row was refused
 * @param message what is wrong with it
 */
public record RowProblem(int line, String column, String message) {

    /** One line for the person fixing the file: {@code line 2, number: an opening needs a number}. */
    public String describe() {
        return "line " + line + ", " + column + ": " + message;
    }
}
