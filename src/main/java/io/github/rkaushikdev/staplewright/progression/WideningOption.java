package io.github.rkaushikdev.staplewright.progression;

/**
 * One way to make a system bigger, and what it costs.
 *
 * @param name what the option is, as the report prints it
 * @param capacity how many cuttable keys the system would hold
 * @param wafersAdded how many more wafers every cylinder would carry
 * @param thinnestSplit the thinnest wafer the option produces, in mils
 * @param note what the bench will say about it
 */
public record WideningOption(String name, int capacity, int wafersAdded, int thinnestSplit, String note) {

    /** An option with nothing to add about it. */
    public WideningOption(String name, int capacity, int wafersAdded, int thinnestSplit) {
        this(name, capacity, wafersAdded, thinnestSplit, "");
    }

    /** How many more keys this gives than the system holds now. */
    public int gainOver(int current) {
        return capacity - current;
    }
}
