package io.github.rkaushikdev.staplewright.services;

import io.github.rkaushikdev.staplewright.ConflictError;
import io.github.rkaushikdev.staplewright.Facts;
import io.github.rkaushikdev.staplewright.NotFoundError;
import io.github.rkaushikdev.staplewright.keys.Bitting;
import io.github.rkaushikdev.staplewright.keys.KeySymbol;
import io.github.rkaushikdev.staplewright.keys.KeyingSchedule;
import io.github.rkaushikdev.staplewright.persistence.Loaders;
import io.github.rkaushikdev.staplewright.persistence.Session;
import io.github.rkaushikdev.staplewright.persistence.SystemRepository;
import io.github.rkaushikdev.staplewright.persistence.models.CylinderRow;
import io.github.rkaushikdev.staplewright.persistence.models.GroupRow;
import io.github.rkaushikdev.staplewright.persistence.models.SystemRow;
import io.github.rkaushikdev.staplewright.pinning.CylinderPinning;
import io.github.rkaushikdev.staplewright.pinning.DriverProfile;
import io.github.rkaushikdev.staplewright.pinning.PinKit;
import io.github.rkaushikdev.staplewright.pinning.PinningSpec;
import io.github.rkaushikdev.staplewright.progression.KeyingGroup;
import io.github.rkaushikdev.staplewright.reports.PinningPages;
import io.github.rkaushikdev.staplewright.util.Text;
import java.util.ArrayList;
import java.util.List;

/**
 * Pinning a cylinder off the schedule, which is the bench half of the work.
 *
 * <p>Everything the bench needs is already in the database and none of it is in one place. The
 * keying group says which symbols the cylinder answers to, the schedule says what each is cut to, the
 * chart says how deep each cut is, and the pinning spec turns that into brass. This walks those four
 * and hands back a chart the bench can work from.
 *
 * <p>It refuses on an uncut symbol rather than skipping it. A cylinder pinned for three of its four
 * keys looks finished and is not, and the fourth key turns up in somebody's hand a week later.
 */
public final class PinningService {

    private final Session session;
    private final PinningSpec spec;
    private final SystemRepository systems;

    public PinningService(Session session, PinningSpec spec) {
        this.session = session;
        this.spec = spec;
        this.systems = new SystemRepository(session);
    }

    public Session session() {
        return session;
    }

    public PinningSpec spec() {
        return spec;
    }

    /** The system by name, refusing one nobody opened. */
    public SystemRow system(String name) {
        return systems.byName(name)
                .orElseThrow(() -> new NotFoundError("no such keying system", Facts.of("system", name)));
    }

    /** The keying group by name, refusing one nobody wrote. */
    public KeyingGroup group(String name) {
        KeyingGroup found = Loaders.loadGroups(session).get(name);
        if (found == null) {
            throw new NotFoundError("no such keying group", Facts.of("group", name));
        }
        return found;
    }

    /** The cuts behind a group, refusing any symbol nobody has cut. */
    public List<Bitting> bittingsFor(String systemName, String groupName) {
        KeyingSchedule schedule = Loaders.loadSchedule(session, system(systemName));
        KeyingGroup group = group(groupName);
        List<Bitting> found = new ArrayList<>();
        for (KeySymbol symbol : group.ordered()) {
            found.add(schedule.bittingOf(symbol)
                    .orElseThrow(() -> new ConflictError(
                            "that key has not been cut yet", Facts.of("group", groupName, "symbol", symbol.render()))));
        }
        return List.copyOf(found);
    }

    public CylinderPinning pin(String systemName, String groupName) {
        return pin(systemName, groupName, DriverProfile.STANDARD);
    }

    /** The stacks for one keying group, ready to draw brass against. */
    public CylinderPinning pin(String systemName, String groupName, DriverProfile profile) {
        return CylinderPinning.pin(spec, bittingsFor(systemName, groupName), profile);
    }

    public List<String> chartFor(String systemName, String groupName) {
        return chartFor(systemName, groupName, "");
    }

    /** The sheet, with the cylinder serial on it when there is one. */
    public List<String> chartFor(String systemName, String groupName, String serial) {
        CylinderPinning pinning = pin(systemName, groupName);
        return PinningPages.cylinderChart(pinning, Text.strip(groupName + "  " + serial));
    }

    /**
     * A pinning and the parts it took.
     *
     * @param pinning the stacks
     * @param summary the parts drawn, as the cabinet sheet prints them
     */
    public record Drawn(CylinderPinning pinning, List<String> summary) {}

    /** Pin it and take the brass, or refuse and take nothing. */
    public Drawn drawFrom(PinKit kit, String systemName, String groupName) {
        CylinderPinning pinning = pin(systemName, groupName);
        kit.draw(pinning.parts());
        return new Drawn(pinning, PinningPages.partsSummary(pinning.parts()));
    }

    /** Every cylinder the register says belongs to this group, by serial. */
    public List<CylinderRow> cylindersFor(String groupName) {
        GroupRow group = groupRow(groupName);
        return session.list(
                "select c from CylinderRow c where c.group = ?1 order by c.serial", CylinderRow.class, group);
    }

    public List<KeySymbol> symbolsFor(String groupName) {
        return group(groupName).ordered();
    }

    private GroupRow groupRow(String name) {
        return session.first("select g from GroupRow g where g.name = ?1", GroupRow.class, name)
                .orElseThrow(() -> new NotFoundError("no such keying group", Facts.of("group", name)));
    }
}
