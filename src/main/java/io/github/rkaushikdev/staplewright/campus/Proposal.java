package io.github.rkaushikdev.staplewright.campus;

import io.github.rkaushikdev.staplewright.keys.KeySymbol;
import java.util.List;
import java.util.Optional;

/**
 * A first draft hierarchy against an access matrix.
 *
 * <p>A proposal, not a schedule: nothing is cut and no bittings exist. It holds the keys the draft
 * would issue, shallow first, and the notes saying where the draft had to guess.
 *
 * @param grants every key in the draft, in schedule order
 * @param notes the places the draft guessed, or the matrix asked for something it could not key
 */
public record Proposal(List<Grant> grants, List<String> notes) {

    public Proposal {
        grants = List.copyOf(grants);
        notes = List.copyOf(notes);
    }

    /** A draft with nothing to say about itself. */
    public Proposal(List<Grant> grants) {
        this(grants, List.of());
    }

    /** Every symbol in the draft, in the order of its grants. */
    public List<KeySymbol> symbols() {
        return grants.stream().map(Grant::symbol).toList();
    }

    /** The grant for one symbol, when the draft holds it. */
    public Optional<Grant> grantFor(KeySymbol symbol) {
        return grants.stream().filter(grant -> grant.symbol().equals(symbol)).findFirst();
    }

    /** Every door a key cut to this symbol would turn, or none for a symbol the draft does not hold. */
    public List<String> opens(KeySymbol symbol) {
        return grantFor(symbol).map(Grant::openings).orElse(List.of());
    }

    /** The grants for change keys, the keys people actually carry. */
    public List<Grant> changeKeys() {
        return grants.stream().filter(grant -> grant.symbol().isChangeKey()).toList();
    }

    /** The grants for every master, the grand masters included. */
    public List<Grant> masters() {
        return grants.stream().filter(grant -> !grant.symbol().isChangeKey()).toList();
    }
}
