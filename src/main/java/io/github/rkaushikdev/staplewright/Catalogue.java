package io.github.rkaushikdev.staplewright;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The refusal codes, gathered, because they are part of the interface.
 *
 * <p>A caller catching a refusal branches on the code, not on the class and not on the message.
 * The class is an implementation detail and the message is prose somebody will improve one day,
 * but the code is a promise: {@code not_found} has meant the same thing since the first release and
 * changing it would break whoever is branching on it.
 *
 * <p>So the codes live in one place, a test makes sure no two refusals share one, and another
 * makes sure every refusal class is on this list. Both exist because a code was duplicated once
 * and the service quietly started mapping two different refusals to the same status.
 */
public final class Catalogue {

    /** Every refusal, with the status the service gives it and whether to try again. */
    public static final List<Refusal> CATALOGUE = List.of(
            new Refusal(InputError.CODE, InputError.class, 422, "the request could not be read", true),
            new Refusal(NotFoundError.CODE, NotFoundError.class, 404, "nothing on record answers to that", false),
            new Refusal(
                    ConflictError.CODE, ConflictError.class, 409, "the record is not in a state that allows it", false),
            new Refusal(
                    CapacityError.CODE, CapacityError.class, 409, "there is not enough of something to do it", true),
            new Refusal(
                    SpecificationError.CODE,
                    SpecificationError.class,
                    400,
                    "the manufacturer specification does not allow it",
                    false),
            new Refusal(BittingError.CODE, BittingError.class, 400, "that is not a cut this chart can produce", false),
            new Refusal(KeywayError.CODE, KeywayError.class, 400, "the keyway sections do not work that way", false),
            new Refusal(
                    HierarchyError.CODE,
                    HierarchyError.class,
                    400,
                    "the symbol does not sit where that needs it to",
                    false),
            new Refusal(
                    ProgressionError.CODE, ProgressionError.class, 400, "the progression cannot produce that", false),
            new Refusal(PinningError.CODE, PinningError.class, 400, "the brass cannot be stacked that way", false),
            new Refusal(HaspError.CODE, HaspError.class, 400, "a refusal with nothing more specific to say", false));

    /** The catalogue keyed by code. */
    public static final Map<String, Refusal> BY_CODE = byCode();

    private Catalogue() {}

    private static Map<String, Refusal> byCode() {
        Map<String, Refusal> found = new LinkedHashMap<>();
        for (Refusal item : CATALOGUE) {
            found.put(item.code(), item);
        }
        return java.util.Collections.unmodifiableMap(found);
    }

    /** The catalogue entry for a refusal, by the closest class that has one. */
    public static Refusal refusalFor(HaspError error) {
        for (Class<?> kind = error.getClass(); kind != null; kind = kind.getSuperclass()) {
            for (Refusal item : CATALOGUE) {
                if (item.kind() == kind) {
                    return item;
                }
            }
        }
        return BY_CODE.get(HaspError.CODE);
    }

    public static int statusFor(HaspError error) {
        return refusalFor(error).status();
    }

    /** Whether the same request could succeed if the caller fixed something. */
    public static boolean worthRetrying(HaspError error) {
        return refusalFor(error).retry();
    }

    /** Every code, sorted. */
    public static List<String> codes() {
        return BY_CODE.keySet().stream().sorted().toList();
    }

    /** The codes as a page, which is what goes in the interface note. */
    public static List<String> catalogueSheet() {
        List<String> lines = new ArrayList<>();
        lines.add(CATALOGUE.size() + " refusal codes");
        for (Refusal item : CATALOGUE) {
            lines.add("  " + item.describe());
        }
        return List.copyOf(lines);
    }
}
