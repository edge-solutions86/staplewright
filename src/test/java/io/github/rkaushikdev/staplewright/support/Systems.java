package io.github.rkaushikdev.staplewright.support;

import io.github.rkaushikdev.staplewright.keys.KeySymbol;
import io.github.rkaushikdev.staplewright.keys.KeyingSchedule;
import io.github.rkaushikdev.staplewright.spec.CuttingRules;
import io.github.rkaushikdev.staplewright.spec.KeywayFamily;
import io.github.rkaushikdev.staplewright.spec.KeywaySection;
import io.github.rkaushikdev.staplewright.spec.RuleSet;
import java.util.function.UnaryOperator;

/** Whole systems the tests build on, one step up from a bare chart. */
public final class Systems {

    private Systems() {}

    /** The campus family. L enters everything, LC and LD are restricted, the leaves are buildings. */
    public static KeywayFamily campusFamily() {
        KeywayFamily family = new KeywayFamily("Sargent LA", new KeywaySection("L", "Sargent"));
        family.add(new KeywaySection("LA", "Sargent"), "L");
        family.add(new KeywaySection("LB", "Sargent"), "L");
        family.add(new KeywaySection("LC", "Sargent", true), "LA");
        family.add(new KeywaySection("LD", "Sargent", true), "LA");
        family.add(new KeywaySection("LE", "Sargent"), "LB");
        return family;
    }

    /** A schedule holding only its top key, on the six pin chart under the standing rules. */
    public static KeyingSchedule emptySchedule() {
        return emptySchedule(new CuttingRules());
    }

    /** A schedule holding only its top key, on the six pin chart under these rules. */
    public static KeyingSchedule emptySchedule(CuttingRules rules) {
        RuleSet ruleset = new RuleSet(Charts.sixPinChart(), rules);
        return new KeyingSchedule("chester park campus", ruleset, campusFamily(), "L");
    }

    /** A schedule holding only its top key, under the standing rules with some of them changed. */
    public static KeyingSchedule emptySchedule(UnaryOperator<CuttingRules> overrides) {
        return emptySchedule(overrides.apply(new CuttingRules()));
    }

    /** Two grand masters, two masters under one of them, four change keys. */
    public static KeyingSchedule smallSchedule() {
        KeyingSchedule schedule = emptySchedule();
        schedule.add(KeySymbol.parse("A"), "LA");
        schedule.add(KeySymbol.parse("B"), "LB");
        schedule.add(KeySymbol.parse("AA"), "LC");
        schedule.add(KeySymbol.parse("AB"), "LD");
        for (int number : new int[] {1, 2}) {
            schedule.add(KeySymbol.parse("AA" + number));
            schedule.add(KeySymbol.parse("AB" + number));
        }
        return schedule;
    }
}
