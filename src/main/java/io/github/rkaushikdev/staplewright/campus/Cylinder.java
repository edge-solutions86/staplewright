package io.github.rkaushikdev.staplewright.campus;

import io.github.rkaushikdev.staplewright.ConflictError;
import io.github.rkaushikdev.staplewright.Facts;
import io.github.rkaushikdev.staplewright.InputError;
import io.github.rkaushikdev.staplewright.util.Text;
import java.util.Optional;

/**
 * One serial numbered cylinder or core.
 *
 * <p>A cylinder gets pinned, installed, pulled, rekeyed, put in a drawer for two years and
 * installed somewhere else, and the shop is expected to know which of those happened. An installed
 * cylinder knows the opening and the position it sits in; a shelved one knows neither.
 *
 * @param serial the number stamped on it
 * @param format the shape of it, which decides what housing takes it
 * @param section the keyway section it is milled for
 * @param chambers how many pin chambers it has
 * @param manufacturer who made it, when the label survived
 * @param spooled whether it carries spool drivers
 * @param installedAt the opening it sits in, set only on an installed cylinder
 * @param position where on that opening it sits, set only on an installed cylinder
 * @param group the keying group it is pinned to, or empty when nobody has said
 */
public record Cylinder(
        String serial,
        CylinderFormat format,
        String section,
        int chambers,
        String manufacturer,
        boolean spooled,
        Optional<String> installedAt,
        Optional<Position> position,
        String group) {

    /**
     * A cylinder, refusing one with no serial or no chambers, and one with an opening but no
     * position or a position but no opening.
     *
     * @throws InputError for a blank serial or fewer than one chamber
     * @throws ConflictError for a cylinder half installed
     */
    public Cylinder {
        if (Text.isBlank(serial)) {
            throw new InputError("a cylinder needs a serial");
        }
        if (chambers < 1) {
            throw new InputError("a cylinder has chambers", Facts.of("serial", serial));
        }
        if (installedAt.isPresent() != position.isPresent()) {
            throw new ConflictError(
                    "an installed cylinder has a position and a shelved one has neither", Facts.of("serial", serial));
        }
    }

    /** A shelved cylinder, maker unknown, in no keying group. */
    public Cylinder(String serial, CylinderFormat format, String section, int chambers) {
        this(serial, format, section, chambers, "");
    }

    /** A shelved cylinder from a known maker, in no keying group. */
    public Cylinder(String serial, CylinderFormat format, String section, int chambers, String manufacturer) {
        this(serial, format, section, chambers, manufacturer, false, Optional.empty(), Optional.empty(), "");
    }

    /** Whether the cylinder is in a door. */
    public boolean isInstalled() {
        return installedAt.isPresent();
    }

    /** Whether it is a small format interchangeable core. */
    public boolean isCore() {
        return format == CylinderFormat.SFIC;
    }

    /** One line for the register: {@code C-1001 mortise LC, MOO-214 lockset}, or on the shelf. */
    public String describe() {
        String where = isInstalled()
                ? installedAt.get() + " " + position.map(Position::value).orElse("")
                : "on the shelf";
        return serial + " " + format.value() + " " + section + ", " + where;
    }

    /** A builder starting from this cylinder, for a copy with some fields changed. */
    public Builder toBuilder() {
        return new Builder(this);
    }

    /** A cylinder put together a field at a time, checked once when it is built. */
    public static final class Builder {
        private String serial;
        private CylinderFormat format;
        private String section;
        private int chambers;
        private String manufacturer;
        private boolean spooled;
        private Optional<String> installedAt;
        private Optional<Position> position;
        private String group;

        private Builder(Cylinder from) {
            serial = from.serial;
            format = from.format;
            section = from.section;
            chambers = from.chambers;
            manufacturer = from.manufacturer;
            spooled = from.spooled;
            installedAt = from.installedAt;
            position = from.position;
            group = from.group;
        }

        public Builder serial(String value) {
            serial = value;
            return this;
        }

        public Builder format(CylinderFormat value) {
            format = value;
            return this;
        }

        public Builder section(String value) {
            section = value;
            return this;
        }

        public Builder chambers(int value) {
            chambers = value;
            return this;
        }

        public Builder manufacturer(String value) {
            manufacturer = value;
            return this;
        }

        public Builder spooled(boolean value) {
            spooled = value;
            return this;
        }

        public Builder installedAt(String value) {
            installedAt = Optional.of(value);
            return this;
        }

        public Builder installedAt(Optional<String> value) {
            installedAt = value;
            return this;
        }

        public Builder position(Position value) {
            position = Optional.of(value);
            return this;
        }

        public Builder position(Optional<Position> value) {
            position = value;
            return this;
        }

        public Builder group(String value) {
            group = value;
            return this;
        }

        public Cylinder build() {
            return new Cylinder(serial, format, section, chambers, manufacturer, spooled, installedAt, position, group);
        }
    }
}
