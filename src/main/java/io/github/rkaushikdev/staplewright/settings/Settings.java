package io.github.rkaushikdev.staplewright.settings;

import io.github.rkaushikdev.staplewright.Facts;
import io.github.rkaushikdev.staplewright.InputError;
import io.github.rkaushikdev.staplewright.keys.Marking;
import io.github.rkaushikdev.staplewright.util.Records;
import io.github.rkaushikdev.staplewright.util.Text;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;

/**
 * Reading the shop's settings from the environment, which is deliberately dull.
 *
 * <p>Names are upper case with a {@value #PREFIX} prefix, values are whole numbers, true and false,
 * or a marking by its name, and anything unreadable is refused rather than falling back to a
 * default nobody intended.
 *
 * <p>Every knob is one row of {@link #SETTINGS}, and the settings are built by walking that table
 * rather than by naming every field. A knob that is in the table is read, and a knob that is not
 * cannot be: a setting written out in three places once shipped unread and its default quietly
 * stood for a year.
 */
public final class Settings {

    /** What every variable this reads starts with. */
    public static final String PREFIX = "STAPLEWRIGHT_";

    /** Every group at its default. */
    public static final ShopSettings DEFAULTS = new ShopSettings();

    /** Every knob this shop has, one row each. */
    public static final List<Setting> SETTINGS = List.of(
            new Setting("cutting", "max_run", "MAX_RUN"),
            new Setting("cutting", "min_distinct", "MIN_DISTINCT"),
            new Setting("deposits", "change_key_cents", "DEPOSIT_CHANGE_CENTS"),
            new Setting("deposits", "master_cents", "DEPOSIT_MASTER_CENTS"),
            new Setting("notices", "reminder_days", "REMINDER_DAYS"),
            new Setting("notices", "first_days", "FIRST_NOTICE_DAYS"),
            new Setting("notices", "second_days", "SECOND_NOTICE_DAYS"),
            new Setting("notices", "hold_days", "HOLD_DAYS"),
            new Setting("notices", "write_off_days", "WRITE_OFF_DAYS"),
            new Setting("scope", "recut_all_on_repin", "STRICT_REPIN"),
            new Setting("scope", "always_rekey_exterior", "REKEY_EXTERIOR"),
            new Setting("scope", "always_rekey_masters", "REKEY_MASTERS"),
            new Setting("scope", "ignore_below_openings", "IGNORE_BELOW"),
            new Setting("rates", "labour_cents_per_hour", "LABOUR_CENTS_HOUR"),
            new Setting("rates", "blank_cents", "BLANK_CENTS"),
            new Setting("boxes", "lowest_level", "BOX_LOWEST_LEVEL"),
            new Setting("boxes", "check_days", "BOX_CHECK_DAYS"),
            new Setting("boxes", "reseal_days", "BOX_RESEAL_DAYS"),
            new Setting("transfers", "confirm_days", "CONFIRM_DAYS"),
            new Setting("transfers", "allow_master_transfer", "MASTER_HANDOVER"),
            new Setting("stamping", "change_keys", "STAMP_CHANGE_KEYS"),
            new Setting("stamping", "masters", "STAMP_MASTERS"),
            new Setting("stamping", "add_do_not_duplicate", "STAMP_DND"),
            new Setting("stamping", "bow_characters", "BOW_CHARACTERS"),
            new Setting("", "quarantine_years", "QUARANTINE_YEARS"),
            new Setting("", "progression_step", "PROGRESSION_STEP"));

    private static final Set<String> TRUE = Set.of("1", "true", "yes", "on");
    private static final Set<String> FALSE = Set.of("0", "false", "no", "off");

    private Settings() {}

    /** Every environment variable this reads, sorted, which is the documentation. */
    public static List<String> variableNames() {
        return SETTINGS.stream().map(item -> PREFIX + item.variable()).sorted().toList();
    }

    /** The group a setting lives in: the settings themselves for a blank name. */
    public static Record groupOf(ShopSettings settings, String name) {
        return name.isEmpty() ? settings : (Record) Records.read(settings, Records.camel(name));
    }

    /** The value a setting has in these settings. */
    public static Object valueOf(ShopSettings settings, Setting item) {
        return Records.read(groupOf(settings, item.group()), Records.camel(item.attribute()));
    }

    /**
     * Build settings from a mapping, leaving anything absent at its default.
     *
     * @throws InputError for a value that cannot be read, or a group its own checks refuse
     */
    public static ShopSettings fromMapping(Map<String, String> source) {
        return fromLookup(source::get);
    }

    /** The same thing, off any lookup of variable names. */
    public static ShopSettings fromLookup(UnaryOperator<String> source) {
        Map<String, Map<String, Object>> changes = new LinkedHashMap<>();
        Map<String, Object> top = new HashMap<>();
        for (Setting item : SETTINGS) {
            Object found = read(source, item.variable(), valueOf(DEFAULTS, item));
            String component = Records.camel(item.attribute());
            if (item.group().isEmpty()) {
                top.put(component, found);
            } else {
                changes.computeIfAbsent(Records.camel(item.group()), name -> new HashMap<>())
                        .put(component, found);
            }
        }
        for (Map.Entry<String, Map<String, Object>> group : changes.entrySet()) {
            Record defaults = (Record) Records.read(DEFAULTS, group.getKey());
            top.put(group.getKey(), Records.replace(defaults, group.getValue()));
        }
        return Records.replace(DEFAULTS, top);
    }

    /** The same thing, off the process environment. */
    public static ShopSettings fromEnvironment() {
        return fromLookup(System::getenv);
    }

    private static Object read(UnaryOperator<String> source, String name, Object fallback) {
        return switch (fallback) {
            case Boolean flag -> flag(source, name, flag);
            case Marking marking -> marking(source, name, marking);
            default -> whole(source, name, (Integer) fallback);
        };
    }

    private static int whole(UnaryOperator<String> source, String name, int fallback) {
        String raw = source.apply(PREFIX + name);
        if (raw == null) {
            return fallback;
        }
        return Text.wholeNumber(raw)
                .filter(value -> value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE)
                .map(Long::intValue)
                .orElseThrow(() ->
                        new InputError("that setting is a whole number", Facts.of("setting", name, "value", raw)));
    }

    private static boolean flag(UnaryOperator<String> source, String name, boolean fallback) {
        String raw = source.apply(PREFIX + name);
        if (raw == null) {
            return fallback;
        }
        String lowered = Text.lower(Text.strip(raw));
        if (TRUE.contains(lowered)) {
            return true;
        }
        if (FALSE.contains(lowered)) {
            return false;
        }
        throw new InputError("that setting is true or false", Facts.of("setting", name, "value", raw));
    }

    /** A marking by its own name, which is what a person would type, or by what it says. */
    private static Marking marking(UnaryOperator<String> source, String name, Marking fallback) {
        String raw = source.apply(PREFIX + name);
        if (raw == null) {
            return fallback;
        }
        String wanted = Text.lower(Text.strip(raw));
        for (Marking item : Marking.values()) {
            if (wanted.equals(Text.lower(item.name())) || wanted.equals(item.value())) {
                return item;
            }
        }
        throw new InputError("nothing gets stamped that way", Facts.of("setting", name, "value", raw));
    }
}
