package io.github.rkaushikdev.staplewright.control;

import io.github.rkaushikdev.staplewright.ConflictError;
import io.github.rkaushikdev.staplewright.Facts;
import io.github.rkaushikdev.staplewright.InputError;
import io.github.rkaushikdev.staplewright.Lifecycle;
import io.github.rkaushikdev.staplewright.Registry;
import io.github.rkaushikdev.staplewright.util.Text;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Every handover anybody has claimed, in the order they were claimed.
 *
 * <p>An unconfirmed handover is worse than no handover, because the key is now with somebody who did
 * not sign for it and the record says it moved. So a key is claimed by one handover at a time, a
 * master does not change hands at all unless the shop's {@link TransferPolicy} says it may, and a
 * claim nobody confirms inside the window lapses rather than standing for ever.
 */
public final class TransferBook implements Iterable<Transfer> {

    private final TransferPolicy policy;
    private final Registry<Transfer> transfers = new Registry<>("transfer", "number");

    /** A book on the shop's standing handover policy. */
    public TransferBook() {
        this(new TransferPolicy());
    }

    public TransferBook(TransferPolicy policy) {
        this.policy = policy;
    }

    /** How long a claim stands here, and whether a master may change hands. */
    public TransferPolicy policy() {
        return policy;
    }

    /** Somebody says they handed a change key over. */
    public Transfer claim(Transfer transfer) {
        return claim(transfer, false);
    }

    /**
     * Somebody says they handed a key over.
     *
     * @throws ConflictError when the handover is not freshly claimed, the key masters and the policy
     *     keeps masters at the counter, or the key is already claimed by an open handover
     */
    public Transfer claim(Transfer transfer, boolean isMaster) {
        if (transfer.state() != TransferState.CLAIMED) {
            throw new ConflictError("a transfer starts claimed", Facts.of("transfer", transfer.number()));
        }
        if (isMaster && !policy.allowMasterTransfer()) {
            throw new ConflictError(
                    "a master does not move between people without the counter",
                    Facts.of("transfer", transfer.number(), "symbol", transfer.symbol()));
        }
        Optional<Transfer> held = openOn(transfer.tag());
        if (held.isPresent()) {
            throw new ConflictError(
                    "that key is already claimed by somebody",
                    Facts.of("tag", transfer.tag(), "transfer", held.get().number()));
        }
        return transfers.add(transfer.number(), transfer);
    }

    /** Put a handover back as it was stored, in whatever state it reached. */
    public Transfer load(Transfer transfer) {
        return transfers.put(transfer.number(), transfer);
    }

    /**
     * The handover with this number.
     *
     * @throws io.github.rkaushikdev.staplewright.NotFoundError when nobody claimed it
     */
    public Transfer transfer(String number) {
        return transfers.get(number);
    }

    /** The person receiving it signs, and only now does the key move. */
    public Transfer confirm(String number, LocalDate when) {
        return confirm(number, when, "");
    }

    public Transfer confirm(String number, LocalDate when, String note) {
        Transfer found = forDecision(number);
        if (when.isBefore(found.claimedOn())) {
            throw new InputError("a handover is confirmed after it was claimed", Facts.of("transfer", number));
        }
        return store(settled(found, TransferState.CONFIRMED, when, note));
    }

    /** The person receiving it says they never got it, which is serious. The note says why. */
    public Transfer refuse(String number, LocalDate when, String note) {
        Transfer found = forDecision(number);
        if (Text.isBlank(note)) {
            throw new InputError("a refusal says why", Facts.of("transfer", number));
        }
        return store(settled(found, TransferState.REFUSED, when, note));
    }

    /** The person who claimed it takes the claim back. */
    public Transfer cancel(String number, LocalDate when) {
        return cancel(number, when, "");
    }

    public Transfer cancel(String number, LocalDate when, String note) {
        return store(settled(forDecision(number), TransferState.CANCELLED, when, note));
    }

    /**
     * The confirmation window ran out, so the key never moved.
     *
     * @throws ConflictError when the claim is still inside its window, naming the day it is due
     */
    public Transfer lapse(String number, LocalDate today) {
        Transfer found = forDecision(number);
        if (!found.isLate(today, policy)) {
            throw new ConflictError(
                    "that claim is still inside its window",
                    Facts.of("transfer", number, "due", found.dueOn(policy).toString()));
        }
        return store(settled(found, TransferState.LAPSED, today, "nobody confirmed inside the window"));
    }

    private static Transfer settled(Transfer found, TransferState state, LocalDate when, String note) {
        return found.toBuilder().state(state).settledOn(when).note(note).build();
    }

    private Transfer forDecision(String number) {
        Transfer found = transfer(number);
        Lifecycle.requireState(
                found.state(),
                List.of(TransferState.CLAIMED),
                "that handover was already settled",
                Facts.of("transfer", number));
        return found;
    }

    private Transfer store(Transfer transfer) {
        return transfers.put(transfer.number(), transfer);
    }

    /** The open handover claiming this key, when there is one. */
    public Optional<Transfer> openOn(String tag) {
        return transfers.first(item -> item.tag().equals(tag) && item.isOpen());
    }

    /** Every handover of one key, settled or not, in the order they were claimed. */
    public List<Transfer> forTag(String tag) {
        return transfers.where(item -> item.tag().equals(tag));
    }

    public List<Transfer> inState(TransferState state) {
        return transfers.where(item -> item.state() == state);
    }

    /** What a person claimed to have handed over. */
    public List<Transfer> claimedBy(String identifier) {
        return transfers.where(item -> item.passedBy().equals(identifier));
    }

    /** What a person still owes a signature on. */
    public List<Transfer> waitingOn(String identifier) {
        return transfers.where(item -> item.passedTo().equals(identifier) && item.isOpen());
    }

    /** Claims still open past their window, the oldest claim first, then by number. */
    public List<Transfer> late(LocalDate today) {
        return transfers.where(item -> item.isLate(today, policy)).stream()
                .sorted(Comparator.comparing(Transfer::claimedOn).thenComparing(Transfer::number))
                .toList();
    }

    /**
     * Who the shop believes has a key on a given day, following the confirmed moves up to that day,
     * or nothing when no confirmed move had happened by then.
     */
    public Optional<String> holderOf(String tag, LocalDate day) {
        return forTag(tag).stream()
                .filter(item -> item.moved()
                        && item.settledOn().isPresent()
                        && !item.settledOn().get().isAfter(day))
                .max(Comparator.comparing((Transfer item) -> item.settledOn().orElse(day)))
                .map(Transfer::passedTo);
    }

    /** Every confirmed move of one key, oldest first. */
    public List<Transfer> chainFor(String tag) {
        return forTag(tag).stream()
                .filter(Transfer::moved)
                .sorted(Comparator.comparing((Transfer item) -> item.settledOn().orElse(item.claimedOn()))
                        .thenComparing(Transfer::number))
                .toList();
    }

    /** Whether a handover with this number is in the book. */
    public boolean contains(String number) {
        return transfers.has(number);
    }

    /** Every handover, in the order they were claimed. */
    @Override
    public Iterator<Transfer> iterator() {
        return transfers.iterator();
    }

    public int size() {
        return transfers.size();
    }

    /** How many keys each person is sitting on past the window unconfirmed, most first. */
    public static Map<String, Integer> unconfirmedExposure(TransferBook book, LocalDate today) {
        return Tallies.count(book.late(today), Transfer::passedTo);
    }

    /** Tags that ended up with somebody outside a list of people, which is the finding, in tag order. */
    public static List<String> keysThatMoved(TransferBook book, Collection<String> people) {
        Set<String> allowed = Set.copyOf(people);
        return book.transfers.values().stream()
                .filter(item -> item.moved() && !allowed.contains(item.passedTo()))
                .map(Transfer::tag)
                .distinct()
                .sorted()
                .toList();
    }
}
