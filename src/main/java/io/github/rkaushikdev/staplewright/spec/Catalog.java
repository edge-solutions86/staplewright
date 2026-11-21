package io.github.rkaushikdev.staplewright.spec;

import io.github.rkaushikdev.staplewright.Facts;
import io.github.rkaushikdev.staplewright.NotFoundError;
import io.github.rkaushikdev.staplewright.pinning.CoreSpec;
import io.github.rkaushikdev.staplewright.pinning.PinningSpec;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/**
 * The charts the shop actually stocks, written down as data.
 *
 * <p>Three manufacturers and five families, copied off the catalogues and checked against a set of
 * measured keys in 2019. They live in code rather than in a table because they are code from the
 * shop's point of view: a chart changing means a new supplier, which means a project, not an
 * afternoon of data entry.
 */
public final class Catalog {

    private static final List<Integer> ZERO_TO_NINE =
            IntStream.range(0, 10).boxed().toList();

    /** The academic buildings, mortise and rim. */
    public static final DepthAndSpaceChart SARGENT_LA = new DepthAndSpaceChart(
            "Sargent",
            "LA six pin",
            6,
            ZERO_TO_NINE,
            328,
            15,
            216,
            156,
            7,
            false,
            2,
            "the academic buildings, mortise and rim");

    /** The residence halls before the 1998 core conversion. */
    public static final DepthAndSpaceChart SCHLAGE_C = new DepthAndSpaceChart(
            "Schlage",
            "C keyway six pin",
            6,
            ZERO_TO_NINE,
            335,
            15,
            231,
            156,
            7,
            false,
            2,
            "the residence halls before the 1998 core conversion");

    /** Every interchangeable core on campus. */
    public static final DepthAndSpaceChart BEST_A2 = new DepthAndSpaceChart(
            "Best",
            "A2 seven pin core",
            7,
            ZERO_TO_NINE,
            320,
            25,
            150,
            155,
            7,
            false,
            2,
            "every interchangeable core on campus");

    /** The maintenance padlocks, and nothing else since 1994. */
    public static final DepthAndSpaceChart CORBIN_60 = new DepthAndSpaceChart(
            "Corbin",
            "60 keyway five pin",
            5,
            List.of(0, 1, 2, 3, 4, 5, 6),
            328,
            20,
            200,
            150,
            4,
            false,
            2,
            "the maintenance padlocks, and nothing else since 1994");

    /** The LA chart after the 2011 recall, when the factory stopped supplying a four. */
    public static final DepthAndSpaceChart SARGENT_LA_SIX_CHAMBER = new DepthAndSpaceChart(
            "Sargent",
            "LA six pin, depth 4 withdrawn",
            6,
            List.of(0, 1, 2, 3, 5, 6, 7, 8, 9),
            328,
            15,
            216,
            156,
            7,
            false,
            2,
            "after the 2011 recall, the factory stopped supplying a four");

    /** Every chart the shop stocks. */
    public static final List<DepthAndSpaceChart> CHARTS =
            List.of(SARGENT_LA, SCHLAGE_C, BEST_A2, CORBIN_60, SARGENT_LA_SIX_CHAMBER);

    /** Every family the shop stocks, in catalogue order. */
    public static final List<Family> FAMILIES = List.of(
            new Family(SARGENT_LA, new PinningSpec(SARGENT_LA, 150, 480, 30)),
            new Family(SCHLAGE_C, new PinningSpec(SCHLAGE_C, 150, 480, 30)),
            new Family(
                    BEST_A2,
                    new PinningSpec(BEST_A2, 100, 600, 25),
                    new CoreSpec(new PinningSpec(BEST_A2, 100, 600, 25), 50)),
            new Family(CORBIN_60, new PinningSpec(CORBIN_60, 160, 470, 40)),
            new Family(SARGENT_LA_SIX_CHAMBER, new PinningSpec(SARGENT_LA_SIX_CHAMBER, 150, 480, 30)));

    private Catalog() {}

    /** Every family the shop stocks, keyed the way a part number reads, in catalogue order. */
    public static Map<String, Family> catalogue() {
        Map<String, Family> found = new LinkedHashMap<>();
        for (Family item : FAMILIES) {
            found.put(item.key(), item);
        }
        return Collections.unmodifiableMap(found);
    }

    /**
     * One family by manufacturer and name.
     *
     * @throws NotFoundError for a family the shop does not stock
     */
    public static Family family(String manufacturer, String name) {
        String key = manufacturer + "/" + name;
        Family found = catalogue().get(key);
        if (found == null) {
            throw new NotFoundError("that family is not one the shop stocks", Facts.of("family", key));
        }
        return found;
    }

    /** The families of one manufacturer, in catalogue order. */
    public static List<Family> familiesFor(String manufacturer) {
        return FAMILIES.stream()
                .filter(item -> item.chart().manufacturer().equals(manufacturer))
                .toList();
    }

    /** Every manufacturer the shop stocks, once each, in name order. */
    public static List<String> manufacturers() {
        return FAMILIES.stream()
                .map(item -> item.chart().manufacturer())
                .distinct()
                .sorted()
                .toList();
    }

    /** The families that take interchangeable cores. */
    public static List<Family> coreFamilies() {
        return FAMILIES.stream().filter(Family::takesCores).toList();
    }
}
