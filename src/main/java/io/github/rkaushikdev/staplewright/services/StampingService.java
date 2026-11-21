package io.github.rkaushikdev.staplewright.services;

import io.github.rkaushikdev.staplewright.ConflictError;
import io.github.rkaushikdev.staplewright.Facts;
import io.github.rkaushikdev.staplewright.NotFoundError;
import io.github.rkaushikdev.staplewright.keys.BlindCode;
import io.github.rkaushikdev.staplewright.keys.BlindCodeBook;
import io.github.rkaushikdev.staplewright.keys.KeySymbol;
import io.github.rkaushikdev.staplewright.keys.Marking;
import io.github.rkaushikdev.staplewright.keys.Stamp;
import io.github.rkaushikdev.staplewright.keys.StampPolicy;
import io.github.rkaushikdev.staplewright.keys.Stamping;
import io.github.rkaushikdev.staplewright.keys.TaggedKey;
import io.github.rkaushikdev.staplewright.persistence.KeyRepository;
import io.github.rkaushikdev.staplewright.persistence.Session;
import io.github.rkaushikdev.staplewright.persistence.models.BlindCodeRow;
import io.github.rkaushikdev.staplewright.persistence.models.IssuedKeyRow;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Stamping over a database, with the code book kept behind one door.
 *
 * <p>The book comes off the table on every call rather than being held on the service, and that is
 * not an oversight. A held book is a copy of the mapping sitting in a process that might be a web
 * worker, and the whole design of a blind code is that the mapping lives in one place somebody
 * controls. {@link #identify} is deliberately narrow: one code in, one symbol out. Anything that
 * hands back the table in bulk is called {@link #theWholeBook} so it is obvious in a log and in a
 * review.
 */
public final class StampingService {

    private final Session session;
    private final StampPolicy policy;
    private final int start;
    private final int step;
    private final BlindCodeRepository codes;
    private final KeyRepository keys;

    public StampingService(Session session) {
        this(session, new StampPolicy());
    }

    public StampingService(Session session, StampPolicy policy) {
        this(session, policy, BlindCodeBook.START, BlindCodeBook.STEP);
    }

    public StampingService(Session session, StampPolicy policy, int start, int step) {
        this.session = session;
        this.policy = policy;
        this.start = start;
        this.step = step;
        this.codes = new BlindCodeRepository(session);
        this.keys = new KeyRepository(session);
    }

    public Session session() {
        return session;
    }

    public StampPolicy policy() {
        return policy;
    }

    public BlindCodeRepository codes() {
        return codes;
    }

    public KeyRepository keys() {
        return keys;
    }

    /** The code book as it stands, rebuilt off the table every time. */
    public BlindCodeBook book() {
        BlindCodeBook found = new BlindCodeBook(start, step);
        for (BlindCodeRow row : codes.all()) {
            found.load(row.getSymbol(), row.getCode());
        }
        return found;
    }

    public BlindCodeRow assign(KeySymbol symbol, LocalDate when) {
        return assign(symbol, when, "");
    }

    /** Give a symbol a code, or hand back the row it already has. */
    public BlindCodeRow assign(KeySymbol symbol, LocalDate when, String by) {
        Optional<BlindCodeRow> held = codes.find(symbol.render());
        if (held.isPresent()) {
            return held.get();
        }
        String code = book().assign(symbol);
        BlindCodeRow row = new BlindCodeRow(symbol.render(), code, when);
        row.setAssignedBy(by);
        session.add(row);
        session.flush();
        return row;
    }

    /** The code a symbol carries, refusing a symbol that has none. */
    public String codeFor(KeySymbol symbol) {
        String rendered = symbol.render();
        return codes.require(rendered, "that symbol has no blind code", Facts.of("symbol", rendered))
                .getCode();
    }

    /** Somebody handed in a key with a number on it. One answer, one call. */
    public String identify(String code) {
        return codes.byCode(code)
                .map(BlindCodeRow::getSymbol)
                .orElseThrow(() -> new NotFoundError("no key was ever stamped with that", Facts.of("code", code)));
    }

    /** Every pair. Named so it stands out in a log and in a review. */
    public List<BlindCode> theWholeBook() {
        return codes.all().stream()
                .map(row -> new BlindCode(row.getCode(), row.getSymbol()))
                .toList();
    }

    public Stamp stampKey(String tag, LocalDate when) {
        return stampKey(tag, when, "");
    }

    /** What goes on one issued key, assigning a code if it needs one. */
    public Stamp stampKey(String tag, LocalDate when, String by) {
        return stampBatch(List.of(tag), when, by).get(0);
    }

    public List<Stamp> stampBatch(List<String> tags, LocalDate when) {
        return stampBatch(tags, when, "");
    }

    /** A run for the machine, assigning every code it needs first. */
    public List<Stamp> stampBatch(List<String> tags, LocalDate when, String by) {
        List<TaggedKey> wanted = new ArrayList<>();
        for (String tag : tags) {
            IssuedKeyRow key = keys.require(tag, "no such key", Facts.of("tag", tag));
            KeySymbol symbol = KeySymbol.parse(key.getSymbol());
            if (policy.markingFor(symbol) == Marking.BLIND) {
                assign(symbol, when, by);
            }
            wanted.add(new TaggedKey(tag, symbol));
        }
        return Stamping.stampRun(wanted, policy, book());
    }

    public List<String> sheet(List<String> tags, LocalDate when) {
        return sheet(tags, when, "");
    }

    /** The run as the sheet that goes to the machine. */
    public List<String> sheet(List<String> tags, LocalDate when, String by) {
        return Stamping.stampSheet(stampBatch(tags, when, by), policy);
    }

    public List<Stamp> everythingOut(LocalDate when) {
        return everythingOut(when, "");
    }

    /** The stamping for every key currently out, for a re-marking run. */
    public List<Stamp> everythingOut(LocalDate when, String by) {
        return stampBatch(keys.out().stream().map(IssuedKeyRow::getTag).toList(), when, by);
    }

    /** Keys that would carry nothing, which the shop cannot identify. */
    public List<String> blankKeys(LocalDate when) {
        return Stamping.unstamped(everythingOut(when));
    }

    /**
     * Take a symbol out of the book, refusing while keys still carry it. A code whose symbol is still
     * on a key in somebody's pocket is a code the shop will be handed back one day, and a book that
     * cannot answer for it is a key nobody can identify.
     */
    public BlindCodeRow retireCode(KeySymbol symbol) {
        String rendered = symbol.render();
        BlindCodeRow row = codes.require(rendered, "that symbol has no blind code", Facts.of("symbol", rendered));
        long stillOut = keys.out().stream()
                .filter(key -> key.getSymbol().equals(rendered))
                .count();
        if (stillOut > 0) {
            throw new ConflictError(
                    "keys stamped with that code are still out", Facts.of("symbol", rendered, "keys", (int) stillOut));
        }
        session.delete(row);
        session.flush();
        return row;
    }

    /** Tags cut to one symbol that are still out, for the retirement check. */
    public static List<String> keysForSymbol(Session session, KeySymbol symbol) {
        return new KeyRepository(session)
                .cutTo(symbol.render()).stream().map(IssuedKeyRow::getTag).toList();
    }

    /** Keys out that this policy would leave blank, without touching the book. */
    public static List<String> unstampedOut(Session session, StampPolicy policy) {
        return new KeyRepository(session)
                .out().stream()
                        .filter(row -> policy.markingFor(KeySymbol.parse(row.getSymbol())) == Marking.NOTHING)
                        .map(IssuedKeyRow::getTag)
                        .sorted()
                        .toList();
    }

    /** Every distinct symbol currently out, which is what needs a code. */
    public static List<String> issuedSymbols(Session session) {
        return session.list(
                "select distinct k.symbol from IssuedKeyRow k where k.state = 'out' order by k.symbol", String.class);
    }
}
