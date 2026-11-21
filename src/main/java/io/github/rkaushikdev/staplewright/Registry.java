package io.github.rkaushikdev.staplewright;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Items under string keys, in the order they were added.
 *
 * <p>Half the classes in this library are the same shape: a map keyed by some identifier, a list
 * holding the order things arrived in, a lookup that refuses with a readable not found, and an add
 * that refuses a duplicate. The storage lives here, and callers keep their own vocabulary on top
 * of it, because the vocabulary is the part that is worth reading.
 *
 * <p>{@code noun} and {@code keyName} are only there so the refusals read like the thing being
 * stored. A missing cylinder says cylinder and names the serial, not keys and items.
 *
 * @param <T> what is stored
 */
public final class Registry<T> implements Iterable<T> {

    private final String noun;
    private final String keyName;
    private final Map<String, T> items = new HashMap<>();
    private final List<String> order = new ArrayList<>();

    public Registry(String noun) {
        this(noun, "id");
    }

    public Registry(String noun, String keyName) {
        this.noun = noun;
        this.keyName = keyName;
    }

    public String noun() {
        return noun;
    }

    public String keyName() {
        return keyName;
    }

    /** Store something new, refusing a key that is already taken. */
    public T add(String key, T item) {
        if (items.containsKey(key)) {
            throw new ConflictError("that " + noun + " is already here", Facts.of(keyName, key));
        }
        items.put(key, item);
        order.add(key);
        return item;
    }

    /** Write over whatever is there, keeping the arrival order. */
    public T put(String key, T item) {
        if (!items.containsKey(key)) {
            order.add(key);
        }
        items.put(key, item);
        return item;
    }

    /** The item under {@code key}, refusing with a not found that names the noun. */
    public T get(String key) {
        if (!items.containsKey(key)) {
            throw new NotFoundError("no such " + noun, Facts.of(keyName, key));
        }
        return items.get(key);
    }

    /** The same lookup for callers that treat missing as an answer. */
    public Optional<T> find(String key) {
        return Optional.ofNullable(items.get(key));
    }

    public T remove(String key) {
        T item = get(key);
        items.remove(key);
        order.remove(key);
        return item;
    }

    public boolean has(String key) {
        return items.containsKey(key);
    }

    /** Whether something is stored under {@code key}. */
    public boolean contains(Object key) {
        return key instanceof String text && items.containsKey(text);
    }

    /** The keys in arrival order. */
    public List<String> keys() {
        return List.copyOf(order);
    }

    public List<String> sortedKeys() {
        return items.keySet().stream().sorted().toList();
    }

    /** Everything in arrival order. */
    public List<T> values() {
        return order.stream().map(items::get).toList();
    }

    /** Everything in key order, which is what most reports print. */
    public List<T> byKey() {
        return sortedKeys().stream().map(items::get).toList();
    }

    /** Everything that passes a test, still in arrival order. */
    public List<T> where(Predicate<? super T> matches) {
        return values().stream().filter(matches).toList();
    }

    /** The earliest arrival that passes a test. */
    public Optional<T> first(Predicate<? super T> matches) {
        return values().stream().filter(matches).findFirst();
    }

    public int size() {
        return items.size();
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    /** Everything in arrival order, over a copy so the registry can change while it is walked. */
    @Override
    public Iterator<T> iterator() {
        return values().iterator();
    }
}
