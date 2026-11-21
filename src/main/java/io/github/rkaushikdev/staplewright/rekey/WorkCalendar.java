package io.github.rkaushikdev.staplewright.rekey;

import io.github.rkaushikdev.staplewright.ConflictError;
import io.github.rkaushikdev.staplewright.Facts;
import io.github.rkaushikdev.staplewright.Registry;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The windows the shop is allowed to work in, and what each one allows.
 *
 * <p>Every window takes work with no restriction; a window takes restricted work only when it was
 * told to. Windows never share a day. The calendar walks its windows in date order.
 */
public final class WorkCalendar implements Iterable<Window> {

    private static final Comparator<Window> IN_ORDER =
            Comparator.comparing(Window::opensOn).thenComparing(Window::name);

    private final Registry<Window> windows = new Registry<>("window", "name");
    private final Map<String, Set<Restriction>> allowed = new HashMap<>();

    /** Add a window that takes only unrestricted work. */
    public Window add(Window window) {
        return add(window, List.of());
    }

    /**
     * Add a window and the restrictions it can work around, refusing one that runs over another.
     *
     * @throws ConflictError when it shares a day with a window already held, naming that one, or
     *     the name is taken
     */
    public Window add(Window window, Collection<Restriction> allows) {
        for (Window held : windows) {
            if (held.overlaps(window)) {
                throw new ConflictError(
                        "that window runs over another one", Facts.of("window", window.name(), "against", held.name()));
            }
        }
        windows.add(window.name(), window);
        Set<Restriction> takes = EnumSet.of(Restriction.NONE);
        takes.addAll(allows);
        allowed.put(window.name(), takes);
        return window;
    }

    /**
     * The window by this name.
     *
     * @throws io.github.rkaushikdev.staplewright.NotFoundError when there is none
     */
    public Window window(String name) {
        return windows.get(name);
    }

    /** Whether a window takes work under a restriction. A window nobody added takes nothing. */
    public boolean allows(String name, Restriction restriction) {
        return allowed.getOrDefault(name, Set.of()).contains(restriction);
    }

    /** Every window by the day it opens and then by name. */
    public List<Window> inOrder() {
        return windows.values().stream().sorted(IN_ORDER).toList();
    }

    /** The windows that take work under a restriction, in date order. */
    public List<Window> windowsFor(Restriction restriction) {
        return inOrder().stream()
                .filter(window -> allows(window.name(), restriction))
                .toList();
    }

    /** How many doors every window together can take. */
    public int totalCapacity() {
        return windows.values().stream().mapToInt(Window::capacity).sum();
    }

    /** Whether a window by this name was added. */
    public boolean contains(Object name) {
        return windows.contains(name);
    }

    /** Every window, in date order. */
    @Override
    public Iterator<Window> iterator() {
        return inOrder().iterator();
    }

    /** How many windows the calendar holds. */
    public int size() {
        return windows.size();
    }
}
