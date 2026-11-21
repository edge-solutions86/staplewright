package io.github.rkaushikdev.staplewright.persistence;

import io.github.rkaushikdev.staplewright.Facts;
import io.github.rkaushikdev.staplewright.NotFoundError;
import io.github.rkaushikdev.staplewright.campus.CoverageMap;
import io.github.rkaushikdev.staplewright.campus.HardwareRegister;
import io.github.rkaushikdev.staplewright.campus.OpeningSchedule;
import io.github.rkaushikdev.staplewright.control.DepositBook;
import io.github.rkaushikdev.staplewright.control.Directory;
import io.github.rkaushikdev.staplewright.control.IssuedKey;
import io.github.rkaushikdev.staplewright.control.KeyLedger;
import io.github.rkaushikdev.staplewright.keys.KeyingSchedule;
import io.github.rkaushikdev.staplewright.persistence.models.SystemRow;
import io.github.rkaushikdev.staplewright.rekey.Quarantine;
import io.github.rkaushikdev.staplewright.settings.ShopSettings;
import io.github.rkaushikdev.staplewright.spec.CuttingRules;
import io.github.rkaushikdev.staplewright.spec.KeywayFamily;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * One session, and everything already built on it: each aggregate loaded once per request instead
 * of once per question.
 *
 * <p>The loaders are cheap to call and expensive to call twice. A campus summary asks for the
 * openings, the coverage, the ledger and the deposits, and the coverage asks for the openings and
 * the groups on its own account, so the openings table gets read three times to answer one
 * question. On a campus with four hundred doors that is not a performance problem yet, and on the
 * one this was written against it already was.
 *
 * <p>So a workspace holds one session and remembers what it has built on it. The lifetime is the
 * request, deliberately: a longer lived cache would be a cache somebody has to remember to clear,
 * and the whole class of bug that comes with that is worse than reading a table twice.
 *
 * <p>Nothing here queries on its own. Every method calls the same loader a caller would have
 * called, which is what makes it safe to drop in: the answer cannot differ, only the number of
 * times it was worked out. A build that refuses holds nothing, so asking again asks again.
 */
public final class Workspace {

    private final Session session;
    private final CuttingRules rules;
    private final int quarantineYears;
    private final SystemRepository systems;
    private final Map<String, Object> held = new HashMap<>();

    /** A workspace under the standing cutting rules and quarantine window. */
    public Workspace(Session session) {
        this(session, new CuttingRules(), Quarantine.RELEASE_AFTER_YEARS);
    }

    /** A workspace that reads schedules under {@code rules} and releases retired cuts after the years given. */
    public Workspace(Session session, CuttingRules rules, int quarantineYears) {
        this.session = session;
        this.rules = rules;
        this.quarantineYears = quarantineYears;
        this.systems = new SystemRepository(session);
    }

    /** A workspace carrying this shop's policy, for a router or a command. */
    public static Workspace forShop(ShopSettings settings, Session session) {
        return new Workspace(session, settings.cutting(), settings.quarantineYears());
    }

    public Session session() {
        return session;
    }

    /** The cutting rules every schedule is read under. */
    public CuttingRules rules() {
        return rules;
    }

    /** How many years a retired cut stays out of the array. */
    public int quarantineYears() {
        return quarantineYears;
    }

    /** The keying systems, by the session this workspace holds. */
    public SystemRepository systems() {
        return systems;
    }

    private <T> T once(String name, Class<T> type, Supplier<T> build) {
        Object found = held.get(name);
        if (found == null) {
            found = build.get();
            held.put(name, found);
        }
        return type.cast(found);
    }

    public OpeningSchedule openings() {
        return once("openings", OpeningSchedule.class, () -> Loaders.loadOpenings(session));
    }

    public HardwareRegister register() {
        return once("register", HardwareRegister.class, () -> Loaders.loadRegister(session));
    }

    public CoverageMap coverage() {
        return once("coverage", CoverageMap.class, () -> Loaders.loadCoverage(session));
    }

    public Directory directory() {
        return once("directory", Directory.class, () -> Loaders.loadDirectory(session));
    }

    public KeyLedger ledger() {
        return once("ledger", KeyLedger.class, () -> Loaders.loadLedger(session));
    }

    public DepositBook deposits() {
        return once("deposits", DepositBook.class, () -> Loaders.loadDeposits(session));
    }

    public Quarantine quarantine() {
        return once("quarantine", Quarantine.class, () -> Loaders.loadQuarantine(session, quarantineYears));
    }

    /** The family stored under {@code name}, held under {@code family:<name>}. */
    public KeywayFamily family(String name) {
        return once("family:" + name, KeywayFamily.class, () -> Loaders.loadFamily(session, name));
    }

    /**
     * The keying system called {@code name}. Not held: the row belongs to the session.
     *
     * @throws NotFoundError when no system has that name
     */
    public SystemRow system(String name) {
        return systems.byName(name)
                .orElseThrow(() -> new NotFoundError("no such keying system", Facts.of("system", name)));
    }

    /**
     * The schedule of the system called {@code name}, held under {@code schedule:<name>}.
     *
     * @throws NotFoundError when no system has that name
     */
    public KeyingSchedule schedule(String name) {
        return once("schedule:" + name, KeyingSchedule.class, () -> Loaders.loadSchedule(session, system(name), rules));
    }

    /** Drop everything held, for a caller that has just written something. */
    public void forget() {
        held.clear();
    }

    /** The names of what has been built so far, sorted. */
    public List<String> loaded() {
        return held.keySet().stream().sorted().toList();
    }

    /**
     * The tags out to one person, which needs the ledger and nothing else.
     *
     * @throws NotFoundError for a person the directory has no record of
     */
    public List<String> personHoldings(String identifier) {
        return ledger().heldBy(identifier).stream().map(IssuedKey::tag).toList();
    }

    /** The tags of the keys out past their due date on {@code on}, the latest first. */
    public List<String> overdue(LocalDate on) {
        return ledger().overdue(on).stream().map(IssuedKey::tag).toList();
    }
}
