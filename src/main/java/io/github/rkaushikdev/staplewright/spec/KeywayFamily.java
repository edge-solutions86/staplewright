package io.github.rkaushikdev.staplewright.spec;

import io.github.rkaushikdev.staplewright.Facts;
import io.github.rkaushikdev.staplewright.KeywayError;
import io.github.rkaushikdev.staplewright.util.Text;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A multiplex family of keyway sections, held as the tree the catalogue draws.
 *
 * <p>The warding down the side of a keyway decides what goes in before any cut depth matters. A key
 * cut on a section high in a multiplex family enters every section below it, which is how one grand
 * master opens buildings that were keyed years apart on different sections. The root section
 * enters everything, and a leaf section enters only itself. Getting that relation backwards is the
 * classic way to hand a department a key that opens more than the schedule says it does.
 *
 * <p>Built by naming the root and then hanging sections off it. A section may be added only once
 * and only under a parent that is already there, so the tree cannot pick up a cycle or a second
 * root by accident.
 */
public final class KeywayFamily implements Iterable<KeywaySection> {

    private final String name;
    private final String root;
    private final Map<String, KeywaySection> sections = new HashMap<>();
    private final Map<String, String> parents = new HashMap<>();
    private final Map<String, List<String>> children = new HashMap<>();

    public KeywayFamily(String name, KeywaySection root) {
        if (Text.isBlank(name)) {
            throw new KeywayError("a family needs a name");
        }
        this.name = name;
        this.root = root.code();
        sections.put(root.code(), root);
        parents.put(root.code(), null);
        children.put(root.code(), new ArrayList<>());
    }

    public String name() {
        return name;
    }

    public KeywaySection root() {
        return sections.get(root);
    }

    /** Hang a section under one that is already in the family. */
    public KeywaySection add(KeywaySection section, String under) {
        if (sections.containsKey(section.code())) {
            throw new KeywayError("that section is already in the family", Facts.of("code", section.code()));
        }
        if (!sections.containsKey(under)) {
            throw new KeywayError("no such parent section", Facts.of("parent", under, "family", name));
        }
        if (!section.manufacturer().equals(root().manufacturer())) {
            throw new KeywayError(
                    "a family holds one manufacturer",
                    Facts.of("family", name, "expected", root().manufacturer(), "got", section.manufacturer()));
        }
        sections.put(section.code(), section);
        parents.put(section.code(), under);
        children.put(section.code(), new ArrayList<>());
        children.get(under).add(section.code());
        return section;
    }

    /** The section by its code, refusing one the family does not hold. */
    public KeywaySection section(String code) {
        KeywaySection found = sections.get(code);
        if (found == null) {
            throw new KeywayError("no such section", Facts.of("code", code, "family", name));
        }
        return found;
    }

    /** The section directly above this one, or nothing for the root. */
    public Optional<KeywaySection> parentOf(String code) {
        section(code);
        return Optional.ofNullable(parents.get(code)).map(sections::get);
    }

    /** The sections hung directly under this one, in the order they were added. */
    public List<KeywaySection> childrenOf(String code) {
        section(code);
        return children.get(code).stream().map(sections::get).toList();
    }

    /** The section itself, then its parent, up to the root. */
    public List<String> ancestry(String code) {
        section(code);
        List<String> line = new ArrayList<>();
        String walk = code;
        while (walk != null) {
            line.add(walk);
            walk = parents.get(walk);
        }
        return List.copyOf(line);
    }

    /** Whether a key cut on one section goes into a cylinder of another. */
    public boolean enters(String cutOn, String keyway) {
        section(cutOn);
        return ancestry(keyway).contains(cutOn);
    }

    /** Every section a key cut on this one enters, itself included, nearest first. */
    public List<String> sectionsEntered(String cutOn) {
        section(cutOn);
        List<String> found = new ArrayList<>();
        Deque<String> pending = new ArrayDeque<>(List.of(cutOn));
        while (!pending.isEmpty()) {
            String code = pending.removeFirst();
            found.add(code);
            pending.addAll(children.get(code));
        }
        return List.copyOf(found);
    }

    /** The lowest section a single key could be cut on to enter both. */
    public String commonSection(String first, String second) {
        List<String> firstLine = ancestry(first);
        Set<String> secondLine = new HashSet<>(ancestry(second));
        for (String code : firstLine) {
            if (secondLine.contains(code)) {
                return code;
            }
        }
        throw new KeywayError(
                "those sections are not in one family", Facts.of("first", first, "second", second, "family", name));
    }

    /** How far below the root a section sits. The root is nought. */
    public int depthOf(String code) {
        return ancestry(code).size() - 1;
    }

    /** The sections with nothing under them, by code. */
    public List<String> leaves() {
        return children.entrySet().stream()
                .filter(entry -> entry.getValue().isEmpty())
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
    }

    /** Every code in the family, sorted. */
    public List<String> codes() {
        return sections.keySet().stream().sorted().toList();
    }

    public boolean contains(String code) {
        return sections.containsKey(code);
    }

    public int size() {
        return sections.size();
    }

    /** Every section, in code order. */
    @Override
    public Iterator<KeywaySection> iterator() {
        return codes().stream().map(sections::get).iterator();
    }
}
