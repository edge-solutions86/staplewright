package io.github.rkaushikdev.staplewright.spec;

import io.github.rkaushikdev.staplewright.ConflictError;
import io.github.rkaushikdev.staplewright.Facts;
import io.github.rkaushikdev.staplewright.InputError;
import io.github.rkaushikdev.staplewright.NotFoundError;
import io.github.rkaushikdev.staplewright.Registry;
import io.github.rkaushikdev.staplewright.util.Text;
import java.time.LocalDate;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Every control key and every core the shop can account for, in the order they were registered.
 *
 * <p>A core goes on the register only when a control key of its own format is already there to
 * pull it, and a control key comes out of service only once no core on the register still answers
 * to it. Withdrawing a key rekeys nothing.
 */
public final class ControlRegister implements Iterable<ControlKey> {

    private final Registry<ControlKey> keys = new Registry<>("control key", "code");
    private final Registry<Core> cores = new Registry<>("core", "serial");

    /**
     * Put a control key on the register.
     *
     * @throws ConflictError for a code already there
     */
    public ControlKey addKey(ControlKey key) {
        return keys.add(key.code(), key);
    }

    /**
     * Put a core on the register, refusing one nothing can pull.
     *
     * @throws ConflictError when no control key on the register has the core's code, the key is the
     *     wrong format for the core, or the serial is already there
     */
    public Core addCore(Core core) {
        if (!keys.contains(core.controlCode())) {
            throw new ConflictError(
                    "no control key on the register pulls that core",
                    Facts.of("serial", core.serial(), "control", core.controlCode()));
        }
        ControlKey held = keys.get(core.controlCode());
        if (held.format() != core.format()) {
            throw new ConflictError(
                    "that control key is the wrong format for the core",
                    Facts.of(
                            "serial",
                            core.serial(),
                            "core_format",
                            core.format().value(),
                            "key_format",
                            held.format().value()));
        }
        return cores.add(core.serial(), core);
    }

    /**
     * One control key by its code.
     *
     * @throws NotFoundError for a code nobody registered
     */
    public ControlKey key(String code) {
        return keys.get(code);
    }

    /**
     * One core by its serial.
     *
     * @throws NotFoundError for a serial nobody registered
     */
    public Core core(String serial) {
        return cores.get(serial);
    }

    /**
     * Take a control key out of service, which does not rekey anything.
     *
     * @throws ConflictError when the key is already withdrawn or cores on the register still answer
     *     to it
     */
    public ControlKey withdraw(String code, LocalDate when) {
        ControlKey key = key(code);
        if (!key.isLive()) {
            throw new ConflictError("that control key is already withdrawn", Facts.of("code", code));
        }
        List<Core> pulled = coresPulledBy(code);
        if (!pulled.isEmpty()) {
            throw new ConflictError(
                    "cores on the register are still pulled by that key",
                    Facts.of("code", code, "cores", pulled.size()));
        }
        return keys.put(code, key.withWithdrawnOn(when));
    }

    /**
     * Record who has a control key, which is the whole of control key control.
     *
     * @throws InputError for a blank holder
     * @throws ConflictError for a withdrawn key
     */
    public ControlKey handTo(String code, String holder) {
        if (Text.isBlank(holder)) {
            throw new InputError("a control key goes to somebody", Facts.of("code", code));
        }
        ControlKey key = key(code);
        if (!key.isLive()) {
            throw new ConflictError("that control key is withdrawn", Facts.of("code", code));
        }
        return keys.put(code, key.withHolder(holder));
    }

    /** The cores one control key pulls, in the order they were registered. */
    public List<Core> coresPulledBy(String code) {
        return cores.where(core -> core.controlCode().equals(code));
    }

    /** The control keys still in service, in the order they were registered. */
    public List<ControlKey> liveKeys() {
        return keys.where(ControlKey::isLive);
    }

    /** The sections the live control keys are cut on, once each, in code order. */
    public List<String> sections() {
        return liveKeys().stream().map(ControlKey::section).distinct().sorted().toList();
    }

    /** Who has a live control key and how many, most first, then by name. */
    public Map<String, Integer> holders() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (ControlKey key : liveKeys()) {
            String name = key.holder().isEmpty() ? ControlKey.NOBODY : key.holder();
            counts.merge(name, 1, Integer::sum);
        }
        Map<String, Integer> ordered = new LinkedHashMap<>();
        counts.entrySet().stream()
                .sorted(Comparator.comparing((Map.Entry<String, Integer> entry) -> -entry.getValue())
                        .thenComparing(Map.Entry::getKey))
                .forEach(entry -> ordered.put(entry.getKey(), entry.getValue()));
        return Collections.unmodifiableMap(ordered);
    }

    /** Cores whose control key has been withdrawn, which is a hole. */
    public List<Core> unaccountedCores() {
        Set<String> live = liveKeys().stream().map(ControlKey::code).collect(Collectors.toSet());
        return cores.where(core -> !live.contains(core.controlCode()));
    }

    /** Whether a control key with this code is on the register. */
    public boolean contains(String code) {
        return keys.contains(code);
    }

    /** Every control key, in the order they were registered. */
    @Override
    public Iterator<ControlKey> iterator() {
        return keys.iterator();
    }

    /** How many control keys are on the register, withdrawn ones included. */
    public int size() {
        return keys.size();
    }
}
