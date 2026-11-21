package io.github.rkaushikdev.staplewright;

import io.github.rkaushikdev.staplewright.util.Literal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Base for everything this library refuses on purpose.
 *
 * <p>The shop cares about two very different kinds of refusal. One is a mistake in what somebody
 * typed, and the counter fixes it and tries again. The other is a statement that the metal cannot
 * do what was asked, and that one has to be readable months later when the same question comes
 * back. So a refusal carries a short stable code, a sentence a person can read, and whatever facts
 * made the answer come out that way.
 *
 * <p>{@link #getMessage()} is the sentence followed by the facts in name order, which is what a
 * terminal prints; {@link #message()} is the sentence alone.
 */
public class HaspError extends RuntimeException {

    /** The code of a refusal with nothing more specific to say. */
    public static final String CODE = "hasp_error";

    /** Every refusal class this library raises, the base first. */
    public static final List<Class<? extends HaspError>> KINDS = List.of(
            HaspError.class,
            InputError.class,
            SpecificationError.class,
            BittingError.class,
            KeywayError.class,
            HierarchyError.class,
            ProgressionError.class,
            PinningError.class,
            CapacityError.class,
            NotFoundError.class,
            ConflictError.class);

    private final String message;
    private final Map<String, Object> facts;

    public HaspError(String message) {
        this(message, Map.of());
    }

    /** A refusal with the facts that produced it, copied so the caller's map stays its own. */
    public HaspError(String message, Map<String, ?> facts) {
        super(message);
        this.message = message;
        this.facts = Collections.unmodifiableMap(new LinkedHashMap<>(facts));
    }

    /** The short stable code a caller branches on. */
    public String code() {
        return CODE;
    }

    /** The sentence, without the facts. */
    public String message() {
        return message;
    }

    /** The facts, in the order they were given. Read only. */
    public Map<String, Object> facts() {
        return facts;
    }

    /** One fact by name, or {@code null} when the refusal carries none by that name. */
    public Object fact(String name) {
        return facts.get(name);
    }

    /** The sentence, then every fact as {@code name=value} in name order, in brackets. */
    @Override
    public String getMessage() {
        if (facts.isEmpty()) {
            return message;
        }
        String rendered = new TreeMap<>(facts)
                .entrySet().stream()
                        .map(entry -> entry.getKey() + "=" + Literal.of(entry.getValue()))
                        .collect(Collectors.joining(", "));
        return message + " (" + rendered + ")";
    }

    /** The shape the service and the printed reports both want: code, message and facts. */
    public Map<String, Object> asDict() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("code", code());
        out.put("message", message);
        out.put("facts", new LinkedHashMap<>(facts));
        return out;
    }

    /**
     * Every code this library can produce, keyed by the code itself.
     *
     * <p>The service needs this to turn a raised error into a status without naming each class by
     * hand, and the tests use it to make sure two classes never quietly claim the same code.
     *
     * @throws SpecificationError when two classes claim one code
     */
    public static Map<String, Class<? extends HaspError>> errorCodes() {
        Map<String, Class<? extends HaspError>> found = new LinkedHashMap<>();
        for (Class<? extends HaspError> kind : KINDS) {
            String code = codeOf(kind);
            Class<? extends HaspError> existing = found.get(code);
            if (existing != null && existing != kind) {
                throw new SpecificationError(
                        "two error classes claim one code",
                        Facts.of("code", code, "first", existing.getSimpleName(), "second", kind.getSimpleName()));
            }
            found.put(code, kind);
        }
        return Collections.unmodifiableMap(found);
    }

    /** The code a refusal class declares in its {@code CODE} constant. */
    public static String codeOf(Class<? extends HaspError> kind) {
        try {
            return (String) kind.getField("CODE").get(null);
        } catch (ReflectiveOperationException missing) {
            throw new SpecificationError("a refusal class declares no code", Facts.of("kind", kind.getSimpleName()));
        }
    }
}
