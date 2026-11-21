package io.github.rkaushikdev.staplewright.control;

import io.github.rkaushikdev.staplewright.ConflictError;
import io.github.rkaushikdev.staplewright.Facts;
import io.github.rkaushikdev.staplewright.InputError;
import io.github.rkaushikdev.staplewright.Lifecycle;
import io.github.rkaushikdev.staplewright.Registry;
import io.github.rkaushikdev.staplewright.util.Text;
import java.time.LocalDate;
import java.util.Iterator;
import java.util.List;

/**
 * Every key request, open and closed, in the order they were raised, and the signature that has to
 * be on one before metal moves.
 *
 * <p>Two rules do the real work. The signature has to reach: an authorizer signs for one
 * department and, usually, a list of buildings. And a master key needs a signature that is allowed
 * to approve masters, which most authorizers are not. Everything else here is bookkeeping around
 * those two.
 */
public final class RequestBook implements Iterable<KeyRequest> {

    private final Directory directory;
    private final Registry<KeyRequest> requests = new Registry<>("request", "number");

    public RequestBook(Directory directory) {
        this.directory = directory;
    }

    /** The directory this book checks keyholders and signatures against. */
    public Directory directory() {
        return directory;
    }

    /**
     * File a new request. It has to start raised, for somebody in the directory, under a number not
     * already used.
     */
    public KeyRequest raiseRequest(KeyRequest request) {
        if (request.state() != RequestState.RAISED) {
            throw new ConflictError("a request starts raised", Facts.of("number", request.number()));
        }
        directory.person(request.keyholder());
        return requests.add(request.number(), request);
    }

    /**
     * The request with this number.
     *
     * @throws io.github.rkaushikdev.staplewright.NotFoundError when no request has it
     */
    public KeyRequest request(String number) {
        return requests.get(number);
    }

    /**
     * Put a request back as it was stored, in whatever state it reached.
     *
     * <p>Used when the book comes off the database. Everything in it went through the front door
     * once and there is no state machine left to walk, only a row to put back.
     */
    public KeyRequest load(KeyRequest request) {
        return requests.put(request.number(), request);
    }

    /** Send a request to whoever signs for that department. */
    public KeyRequest submit(String number) {
        KeyRequest request = request(number);
        if (request.state() != RequestState.RAISED) {
            throw new ConflictError(
                    "only a raised request can be submitted",
                    Facts.of("number", number, "state", request.state().value()));
        }
        return store(request.toBuilder().state(RequestState.SUBMITTED).build());
    }

    /** Sign a request off with nothing written against it. */
    public KeyRequest approve(String number, String signer, LocalDate when) {
        return approve(number, signer, when, "");
    }

    /**
     * Sign a request off, refusing a signature that does not reach.
     *
     * @throws ConflictError when the request is not submitted, the keyholder may not hold keys, the
     *     signature does not cover the keyholder's department and the building, or the key masters
     *     and the signature may not approve masters
     */
    public KeyRequest approve(String number, String signer, LocalDate when, String note) {
        KeyRequest request = forDecision(number);
        Keyholder person = directory.person(request.keyholder());
        if (!person.mayHoldKeys(when)) {
            throw new ConflictError(
                    "that keyholder may not hold keys",
                    Facts.of(
                            "number",
                            number,
                            "keyholder",
                            request.keyholder(),
                            "standing",
                            person.standing().value()));
        }
        if (!directory.mayAuthorize(signer, person.department(), request.building(), when)) {
            throw new ConflictError(
                    "that signature does not reach this request",
                    Facts.of(
                            "number",
                            number,
                            "signer",
                            signer,
                            "department",
                            person.department(),
                            "building",
                            request.building()));
        }
        if (request.isMaster() && !directory.authorizer(signer).mayApproveMasters()) {
            throw new ConflictError(
                    "that signature is not allowed to approve a master",
                    Facts.of("number", number, "signer", signer, "symbol", request.symbol()));
        }
        return store(decided(request, RequestState.APPROVED, when, signer, note));
    }

    /** Turn a request down. The note is required, not optional. */
    public KeyRequest deny(String number, String signer, LocalDate when, String note) {
        KeyRequest request = forDecision(number);
        if (Text.isBlank(note)) {
            throw new InputError("a denial says why", Facts.of("number", number));
        }
        return store(decided(request, RequestState.DENIED, when, signer, note));
    }

    /** The department changed its mind, which is not the same as a denial. */
    public KeyRequest withdraw(String number, LocalDate when) {
        return withdraw(number, when, "");
    }

    public KeyRequest withdraw(String number, LocalDate when, String note) {
        KeyRequest request = request(number);
        if (!request.isOpen()) {
            throw new ConflictError(
                    "only an open request can be withdrawn",
                    Facts.of("number", number, "state", request.state().value()));
        }
        return store(request.toBuilder()
                .state(RequestState.WITHDRAWN)
                .decidedOn(when)
                .note(note)
                .build());
    }

    /** The key went over the counter. Only an approved request can. */
    public KeyRequest markFilled(String number) {
        KeyRequest request = request(number);
        if (request.state() != RequestState.APPROVED) {
            throw new ConflictError(
                    "only an approved request gets filled",
                    Facts.of("number", number, "state", request.state().value()));
        }
        return store(request.toBuilder().state(RequestState.FILLED).build());
    }

    private static KeyRequest decided(
            KeyRequest request, RequestState state, LocalDate when, String signer, String note) {
        return request.toBuilder()
                .state(state)
                .decidedOn(when)
                .decidedBy(signer)
                .note(note)
                .build();
    }

    private KeyRequest forDecision(String number) {
        KeyRequest request = request(number);
        Lifecycle.requireState(
                request.state(),
                List.of(RequestState.SUBMITTED),
                "only a submitted request can be decided",
                Facts.of("number", number));
        return request;
    }

    private KeyRequest store(KeyRequest request) {
        return requests.put(request.number(), request);
    }

    /** The requests in one state, in the order they were raised. */
    public List<KeyRequest> inState(RequestState state) {
        return requests.where(request -> request.state() == state);
    }

    /** The requests still raised or submitted, in the order they were raised. */
    public List<KeyRequest> openRequests() {
        return requests.where(KeyRequest::isOpen);
    }

    /** Everything one person has asked for. They have to be in the directory. */
    public List<KeyRequest> forKeyholder(String identifier) {
        directory.person(identifier);
        return requests.where(request -> request.keyholder().equals(identifier));
    }

    /** Everything one signer has decided. */
    public List<KeyRequest> signedBy(String signer) {
        return requests.where(request -> request.decidedBy().equals(signer));
    }

    /** Submitted requests a department still owes a decision on. */
    public List<KeyRequest> waitingOn(String department) {
        return requests.where(request -> request.state() == RequestState.SUBMITTED
                && directory.person(request.keyholder()).department().equals(department));
    }

    /** Whether a request with this number is in the book. */
    public boolean contains(String number) {
        return requests.has(number);
    }

    /** Every request, in the order they were raised. */
    @Override
    public Iterator<KeyRequest> iterator() {
        return requests.iterator();
    }

    public int size() {
        return requests.size();
    }
}
