package io.github.rkaushikdev.staplewright.persistence;

import io.github.rkaushikdev.staplewright.persistence.models.Rows;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * The shape every table follows: the column widths, the way constraints are named, and the order
 * the tables are built in.
 *
 * <p>Constraint names are fixed by the migrations rather than left to the database, because a
 * migration that drops a constraint does so by name and every database makes up different ones.
 * Naming them by one convention is the difference between a downgrade that works and one that has
 * to be hand edited.
 *
 * <p>Column widths are declared once. They are short on purpose: a key symbol is eight characters
 * and a room number is sixteen, and a column wide enough to hold a paragraph invites somebody to put
 * a paragraph in it.
 */
public final class Schema {

    /** Codes, symbols, part numbers. Everything the shop writes by hand. */
    public static final int SHORT = 32;

    /** Names of people and buildings. */
    public static final int MEDIUM = 128;

    /** Notes. Long enough for a sentence, short enough to discourage an essay. */
    public static final int LONG = 512;

    /** How each kind of constraint is named, by its prefix. */
    public static final Map<String, String> NAMING_CONVENTION = Map.of(
            "ix", "ix_<table>_<columns>",
            "uq", "uq_<table>_<columns>",
            "ck", "ck_<table>_<name>",
            "fk", "fk_<table>_<first column>_<referred table>",
            "pk", "pk_<table>");

    private Schema() {}

    /** Every row class the schema maps. */
    public static List<Class<?>> rows() {
        return Rows.ALL;
    }

    /** The table a row class maps to. */
    public static String tableOf(Class<?> row) {
        Table table = row.getAnnotation(Table.class);
        if (table == null || row.getAnnotation(Entity.class) == null) {
            throw new IllegalArgumentException(row.getSimpleName() + " is not a mapped row");
        }
        return table.name();
    }

    /**
     * Every table the schema maps, in the order they can be built: a table after every table it
     * refers to, and otherwise by name.
     */
    public static List<String> tableNames() {
        Map<String, Set<String>> needs = new TreeMap<>();
        for (Class<?> row : rows()) {
            String table = tableOf(row);
            Set<String> referred = needs.computeIfAbsent(table, name -> new TreeSet<>());
            for (Class<?> kind = row; kind != null && kind != Object.class; kind = kind.getSuperclass()) {
                for (Field field : kind.getDeclaredFields()) {
                    if (field.isAnnotationPresent(ManyToOne.class) && !Modifier.isStatic(field.getModifiers())) {
                        String target = tableOf(field.getType());
                        if (!target.equals(table)) {
                            referred.add(target);
                        }
                    }
                }
            }
        }
        List<String> order = new ArrayList<>();
        Map<String, Set<String>> pending = new HashMap<>();
        needs.forEach((table, referred) -> pending.put(table, new TreeSet<>(referred)));
        while (!pending.isEmpty()) {
            String next = pending.entrySet().stream()
                    .filter(entry -> order.containsAll(entry.getValue()))
                    .map(Map.Entry::getKey)
                    .sorted()
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("the tables refer to each other in a circle"));
            order.add(next);
            pending.remove(next);
        }
        return List.copyOf(order);
    }
}
