package io.github.rkaushikdev.staplewright;

import io.github.rkaushikdev.staplewright.keys.Bitting;
import io.github.rkaushikdev.staplewright.keys.KeySymbol;
import io.github.rkaushikdev.staplewright.settings.Settings;
import io.github.rkaushikdev.staplewright.settings.ShopSettings;
import io.github.rkaushikdev.staplewright.spec.CuttingRules;
import io.github.rkaushikdev.staplewright.spec.DepthAndSpaceChart;
import io.github.rkaushikdev.staplewright.spec.KeywayFamily;
import io.github.rkaushikdev.staplewright.spec.KeywaySection;
import java.util.List;

/**
 * Key control and master key system design for the campus lock shop.
 *
 * <p>This is a library first and a service second. Everything below the {@code persistence}
 * package knows there is a database; nothing above it does, and that line is the reason a keying
 * system can be worked out on a bench with no database anywhere near it.
 *
 * <p>The types in {@link #EXPORTS} are the ones a caller outside the library is meant to reach for.
 * Everything else is reachable and none of it is promised: the mappers, the repositories and the
 * row classes move whenever the schema does. Where to start, by what you are holding:
 *
 * <ul>
 *   <li>a chart and a set of symbols, and no database at all: the {@code spec}, {@code keys} and
 *       {@code progression} packages
 *   <li>a database and a question about it: the {@code services} package and the persistence
 *       {@code Workspace}
 *   <li>a printed page somebody has asked for: the {@code reports} package
 *   <li>a terminal: the {@code staplewright} command
 * </ul>
 */
public final class Staplewright {

    /** The version of this library, which is the latest release. */
    public static final String VERSION = "1.0.0";

    /** The types this library promises not to move, sorted by name. */
    public static final List<Class<?>> EXPORTS = List.of(
            Bitting.class,
            BittingError.class,
            CapacityError.class,
            ConflictError.class,
            CuttingRules.class,
            DepthAndSpaceChart.class,
            HaspError.class,
            HierarchyError.class,
            InputError.class,
            KeySymbol.class,
            KeywayError.class,
            KeywayFamily.class,
            KeywaySection.class,
            NotFoundError.class,
            PinningError.class,
            ProgressionError.class,
            Settings.class,
            ShopSettings.class,
            SpecificationError.class);

    private Staplewright() {}
}
