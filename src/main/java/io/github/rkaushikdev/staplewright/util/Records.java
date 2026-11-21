package io.github.rkaushikdev.staplewright.util;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;

/**
 * Reading and replacing a record's components by name, for tables of knobs that are walked rather
 * than written out field by field.
 *
 * <p>A replacement goes back through the record's canonical constructor, so every check the record
 * makes on itself still runs, and whatever it refuses with is what the caller sees.
 */
public final class Records {

    private Records() {}

    /** The component called {@code name}, when the record has one. */
    public static Optional<RecordComponent> component(Class<?> type, String name) {
        RecordComponent[] components = type.getRecordComponents();
        if (components == null) {
            return Optional.empty();
        }
        return Arrays.stream(components)
                .filter(item -> item.getName().equals(name))
                .findFirst();
    }

    /** The value of the component called {@code name}. */
    public static Object read(Record record, String name) {
        RecordComponent found = component(record.getClass(), name)
                .orElseThrow(() -> new IllegalArgumentException(
                        "no component " + name + " on " + record.getClass().getSimpleName()));
        try {
            return found.getAccessor().invoke(record);
        } catch (IllegalAccessException | InvocationTargetException trouble) {
            throw new IllegalStateException("cannot read " + name, trouble);
        }
    }

    /** A copy of {@code record} with the named components replaced. */
    public static <R extends Record> R replace(R record, Map<String, ?> changes) {
        Class<?> type = record.getClass();
        RecordComponent[] components = type.getRecordComponents();
        for (String name : changes.keySet()) {
            if (component(type, name).isEmpty()) {
                throw new IllegalArgumentException("no component " + name + " on " + type.getSimpleName());
            }
        }
        Object[] values = new Object[components.length];
        Class<?>[] types = new Class<?>[components.length];
        for (int index = 0; index < components.length; index++) {
            String name = components[index].getName();
            types[index] = components[index].getType();
            values[index] = changes.containsKey(name) ? changes.get(name) : read(record, name);
        }
        try {
            Constructor<?> canonical = type.getDeclaredConstructor(types);
            @SuppressWarnings("unchecked")
            R built = (R) canonical.newInstance(values);
            return built;
        } catch (InvocationTargetException refused) {
            if (refused.getCause() instanceof RuntimeException cause) {
                throw cause;
            }
            throw new IllegalStateException("cannot build " + type.getSimpleName(), refused.getCause());
        } catch (ReflectiveOperationException trouble) {
            throw new IllegalStateException("cannot build " + type.getSimpleName(), trouble);
        }
    }

    /** {@code max_run} as a component name, {@code maxRun}. */
    public static String camel(String snake) {
        StringBuilder out = new StringBuilder();
        boolean upper = false;
        for (char at : snake.toCharArray()) {
            if (at == '_') {
                upper = true;
            } else {
                out.append(upper ? Character.toUpperCase(at) : at);
                upper = false;
            }
        }
        return out.toString();
    }
}
