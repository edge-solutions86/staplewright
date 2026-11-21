package io.github.rkaushikdev.staplewright.spec;

import java.util.Comparator;
import java.util.List;
import java.util.stream.StreamSupport;

/** Questions about blanks and sections that cross more than one of them. */
public final class Keyways {

    private Keyways() {}

    /** Every blank cut on one section, deepest stock first, then by part number. */
    public static List<Blank> blanksFor(Iterable<Blank> blanks, String section) {
        return StreamSupport.stream(blanks.spliterator(), false)
                .filter(blank -> blank.section().equals(section))
                .sorted(Comparator.comparingInt((Blank blank) -> -blank.stock()).thenComparing(Blank::partNumber))
                .toList();
    }

    /** The restricted sections in a family, in code order. */
    public static List<String> restrictedSections(KeywayFamily family) {
        return StreamSupport.stream(family.spliterator(), false)
                .filter(KeywaySection::restricted)
                .map(KeywaySection::code)
                .toList();
    }
}
