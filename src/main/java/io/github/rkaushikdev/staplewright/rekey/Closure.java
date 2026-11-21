package io.github.rkaushikdev.staplewright.rekey;

import io.github.rkaushikdev.staplewright.campus.CoverageMap;
import io.github.rkaushikdev.staplewright.campus.Cylinder;
import io.github.rkaushikdev.staplewright.keys.KeySymbol;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Every symbol that has to be recut and every cylinder that comes apart when some keys go missing.
 *
 * <p>The cheap answer is to repin the one cylinder and cut one new key. The expensive answer is
 * that the missing key was a master, so every cylinder that master turns has to come apart. There
 * is a third answer that catches people out. Some shops hold that a repinned cylinder retires every
 * cut in it, not just the compromised one, because the array position is now known to somebody.
 * Turn that policy on and the work does not stop at one pass: recutting the other keys drags in
 * every other cylinder those keys operate, and that drags in their masters, and the set grows until
 * it stops growing. The fixpoint is usually small and occasionally it is the whole system, which is
 * exactly the thing worth knowing before the crew is dispatched.
 *
 * @param symbols the symbols to recut, shallowest first and then as written
 * @param cylinders the serials of the installed cylinders to take apart, in serial order
 */
public record Closure(List<KeySymbol> symbols, List<String> cylinders) {

    private static final Comparator<KeySymbol> SHALLOW_FIRST =
            Comparator.comparingInt(KeySymbol::level).thenComparing(KeySymbol::render);

    public Closure {
        symbols = List.copyOf(symbols);
        cylinders = List.copyOf(cylinders);
    }

    /** The closure in one pass, with the strict policy off. */
    public static Closure of(CoverageMap coverage, Iterable<KeySymbol> symbols) {
        return of(coverage, symbols, false);
    }

    /**
     * The closure of some symbols over the cylinders they turn. With {@code recutAllOnRepin} off
     * this is one pass. With it on it is a fixpoint, and the loop is written out rather than made
     * clever because somebody will read it while deciding whether to spend a week.
     */
    public static Closure of(CoverageMap coverage, Iterable<KeySymbol> symbols, boolean recutAllOnRepin) {
        Set<KeySymbol> wanted = new HashSet<>();
        symbols.forEach(wanted::add);
        Set<String> serials = new HashSet<>();
        while (true) {
            int symbolsBefore = wanted.size();
            int serialsBefore = serials.size();
            for (KeySymbol symbol : new ArrayList<>(wanted)) {
                for (Cylinder cylinder : coverage.cylindersFor(symbol)) {
                    if (cylinder.isInstalled()) {
                        serials.add(cylinder.serial());
                    }
                }
            }
            if (recutAllOnRepin) {
                for (String serial : new ArrayList<>(serials)) {
                    Cylinder cylinder = coverage.register().cylinder(serial);
                    coverage.groupOf(cylinder).ifPresent(group -> wanted.addAll(group.symbols()));
                }
            }
            if (wanted.size() == symbolsBefore && serials.size() == serialsBefore) {
                break;
            }
        }
        return new Closure(
                wanted.stream().sorted(SHALLOW_FIRST).toList(),
                serials.stream().sorted().toList());
    }
}
