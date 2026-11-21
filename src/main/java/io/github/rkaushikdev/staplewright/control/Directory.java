package io.github.rkaushikdev.staplewright.control;

import io.github.rkaushikdev.staplewright.ConflictError;
import io.github.rkaushikdev.staplewright.Facts;
import io.github.rkaushikdev.staplewright.Registry;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

/**
 * Every keyholder and every authorizer, in one place.
 *
 * <p>The directory is where standing lives. Separating, suspending, reinstating and extending a
 * record all happen here, and every other part of key control asks the directory whether a person
 * may hold keys today rather than deciding for itself. It walks its keyholders in identifier order.
 */
public final class Directory implements Iterable<Keyholder> {

    private final Registry<Keyholder> people = new Registry<>("keyholder", "identifier");
    private final Registry<Authorizer> signers = new Registry<>("authorizer", "identifier");

    /** Put somebody new in the directory, refusing an identifier already in use. */
    public Keyholder add(Keyholder person) {
        return people.add(person.identifier(), person);
    }

    /**
     * The keyholder with this identifier.
     *
     * @throws io.github.rkaushikdev.staplewright.NotFoundError when nobody has it
     */
    public Keyholder person(String identifier) {
        return people.get(identifier);
    }

    /** Mark somebody as gone. Their keys are somebody else's problem. */
    public Keyholder separate(String identifier, LocalDate when) {
        Keyholder person = person(identifier);
        if (person.standing() == Standing.SEPARATED) {
            throw new ConflictError("that keyholder has already separated", Facts.of("identifier", identifier));
        }
        return people.put(identifier, person.separated(when));
    }

    /** Stop somebody's keys without closing their record. */
    public Keyholder suspend(String identifier) {
        Keyholder person = person(identifier);
        if (person.standing() == Standing.SEPARATED) {
            throw new ConflictError("a separated keyholder cannot be suspended", Facts.of("identifier", identifier));
        }
        return people.put(identifier, person.suspended());
    }

    /** Somebody came back, or the suspension was lifted. */
    public Keyholder reinstate(String identifier) {
        return people.put(identifier, person(identifier).reinstated());
    }

    /** Push a contract end date out. It cannot be pulled backward here. */
    public Keyholder extend(String identifier, LocalDate until) {
        Keyholder person = person(identifier);
        if (person.endsOn().isEmpty()) {
            throw new ConflictError("that record has no end date to extend", Facts.of("identifier", identifier));
        }
        LocalDate endsOn = person.endsOn().get();
        if (!until.isAfter(endsOn)) {
            throw new ConflictError(
                    "an extension moves the date forward",
                    Facts.of("identifier", identifier, "ends_on", endsOn.toString(), "asked", until.toString()));
        }
        return people.put(identifier, person.extendedTo(until));
    }

    /** Let somebody sign. They have to be in the directory first. */
    public Authorizer addAuthorizer(Authorizer authorizer) {
        person(authorizer.identifier());
        return signers.add(authorizer.identifier(), authorizer);
    }

    /**
     * The signature on file for this identifier.
     *
     * @throws io.github.rkaushikdev.staplewright.NotFoundError when that person does not sign
     */
    public Authorizer authorizer(String identifier) {
        return signers.get(identifier);
    }

    /** Whether a signature counts today, for this department and building. */
    public boolean mayAuthorize(String identifier, String department, String building, LocalDate today) {
        Optional<Authorizer> signer = signers.find(identifier);
        if (signer.isEmpty() || !person(identifier).mayHoldKeys(today)) {
            return false;
        }
        return signer.get().covers(department, building);
    }

    /** Everybody in one department, in identifier order. */
    public List<Keyholder> inDepartment(String department) {
        return people.where(person -> person.department().equals(department)).stream()
                .sorted(Comparator.comparing(Keyholder::identifier))
                .toList();
    }

    /** Everybody who may hold keys today, in identifier order. */
    public List<Keyholder> active(LocalDate today) {
        return people.where(person -> person.mayHoldKeys(today)).stream()
                .sorted(Comparator.comparing(Keyholder::identifier))
                .toList();
    }

    /** Records that ran past their end date and nobody closed, the longest lapsed first. */
    public List<Keyholder> lapsed(LocalDate today) {
        return people.where(person -> person.standing() == Standing.ACTIVE && person.lapsedOn(today)).stream()
                .sorted(Comparator.comparing(
                                (Keyholder person) -> person.endsOn().orElse(today))
                        .thenComparing(Keyholder::identifier))
                .toList();
    }

    /** Every signature on file, in identifier order. */
    public List<Authorizer> authorizers() {
        return signers.byKey();
    }

    /** Whether somebody with this identifier is in the directory. */
    public boolean contains(String identifier) {
        return people.has(identifier);
    }

    /** Every keyholder, in identifier order. */
    @Override
    public Iterator<Keyholder> iterator() {
        return people.byKey().iterator();
    }

    public int size() {
        return people.size();
    }
}
