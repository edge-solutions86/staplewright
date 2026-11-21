package io.github.rkaushikdev.staplewright.support;

import static io.github.rkaushikdev.staplewright.support.Campus.aCampus;
import static io.github.rkaushikdev.staplewright.support.People.TODAY;
import static io.github.rkaushikdev.staplewright.support.People.aDirectory;

import io.github.rkaushikdev.staplewright.campus.CoverageMap;
import io.github.rkaushikdev.staplewright.campus.Cylinder;
import io.github.rkaushikdev.staplewright.campus.CylinderFormat;
import io.github.rkaushikdev.staplewright.campus.HardwareRegister;
import io.github.rkaushikdev.staplewright.campus.Position;
import io.github.rkaushikdev.staplewright.control.KeyLedger;
import io.github.rkaushikdev.staplewright.keys.KeySymbol;
import io.github.rkaushikdev.staplewright.progression.KeyingGroup;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A campus with cylinders in the doors and keys in people's pockets.
 *
 * <p>Three records have to agree for any coverage question to mean anything, so they are built
 * together here rather than in every test that needs them. Five keying groups hang off the top
 * key: the Moore offices and storeroom under master AA, the classroom under master AB, the Moore
 * entrance on grand master A itself, and the shops under grand master B. Six Sargent mortise
 * cylinders are installed in the
 * lockset of six doors, one per group except the offices, which take two, and one more cylinder
 * sits on the shelf with no group. Five keys are out on the campus directory.
 */
public final class Keyed {

    /** One cylinder installed in the lockset of one opening and pinned to one keying group. */
    public record Installed(String serial, String opening, String group) {}

    /** Each keying group by name and the symbols it takes, as the shop writes them. */
    public static final Map<String, List<String>> GROUPS = groups();

    /** The cylinders in the doors, in the order they went in. */
    public static final List<Installed> INSTALLED = List.of(
            new Installed("C-1001", "MOO-214", "moore offices"),
            new Installed("C-1002", "MOO-216", "moore offices"),
            new Installed("C-1003", "MOO-218", "moore storeroom"),
            new Installed("C-1004", "MOO-110", "moore classroom"),
            new Installed("C-1005", "MOO-101", "moore entrance"),
            new Installed("C-1006", "HEI-001", "shops"));

    private Keyed() {}

    private static Map<String, List<String>> groups() {
        Map<String, List<String>> groups = new LinkedHashMap<>();
        groups.put("moore offices", List.of("GGM", "A", "AA", "AA1"));
        groups.put("moore storeroom", List.of("GGM", "A", "AA", "AA2"));
        groups.put("moore classroom", List.of("GGM", "A", "AB1"));
        groups.put("moore entrance", List.of("GGM", "A"));
        groups.put("shops", List.of("GGM", "B", "BA1"));
        return Collections.unmodifiableMap(groups);
    }

    /** The keying groups by name, in the order {@link #GROUPS} lists them. */
    public static Map<String, KeyingGroup> keyingGroups() {
        Map<String, KeyingGroup> found = new LinkedHashMap<>();
        GROUPS.forEach((name, symbols) -> found.put(
                name,
                new KeyingGroup(name, symbols.stream().map(KeySymbol::parse).toList())));
        return found;
    }

    public static HardwareRegister aRegister() {
        HardwareRegister register = new HardwareRegister(aCampus());
        for (Installed installed : INSTALLED) {
            register.add(new Cylinder(installed.serial(), CylinderFormat.MORTISE, "LC", 6, "Sargent"));
            register.install(installed.serial(), installed.opening(), Position.LOCKSET);
            register.assignGroup(installed.serial(), installed.group());
        }
        register.add(new Cylinder("C-1099", CylinderFormat.MORTISE, "LC", 6));
        return register;
    }

    public static CoverageMap aCoverage() {
        return new CoverageMap(aRegister(), keyingGroups());
    }

    public static KeyLedger aLedger() {
        KeyLedger ledger = new KeyLedger(aDirectory());
        ledger.issue("41207", "AA1", "kmaki", TODAY);
        ledger.issue("41208", "AA1", "dsalo", TODAY);
        ledger.issue("41209", "AA", "rvirta", TODAY);
        ledger.issue("41210", "A", "phamm", TODAY);
        ledger.issue("41211", "GGM", "phamm", TODAY);
        return ledger;
    }
}
