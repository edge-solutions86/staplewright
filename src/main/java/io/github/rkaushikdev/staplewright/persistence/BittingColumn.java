package io.github.rkaushikdev.staplewright.persistence;

import io.github.rkaushikdev.staplewright.keys.Bitting;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * A cut sequence, stored the way the shop writes it.
 *
 * <p>A bitting wants to be readable in the database, because the first thing anybody does with the
 * key table is open it in a client and look. So it goes in dotted when the chart goes past nine
 * depths and packed when it does not, and comes back as a {@link Bitting} rather than as a string
 * somebody has to remember to parse.
 */
@Converter(autoApply = true)
public final class BittingColumn implements AttributeConverter<Bitting, String> {

    /** How wide the column is. */
    public static final int WIDTH = 48;

    /**
     * The text stored for a value: a bitting as it renders, text read as a bitting first, nothing
     * as nothing.
     *
     * @throws IllegalArgumentException for anything else
     * @throws io.github.rkaushikdev.staplewright.InputError for text that is not a bitting
     */
    public static String bind(Object value) {
        return switch (value) {
            case null -> null;
            case Bitting bitting -> bitting.render();
            case String text -> Bitting.parse(text).render();
            default ->
                throw new IllegalArgumentException(
                        "cannot store " + value.getClass().getSimpleName() + " as a bitting");
        };
    }

    /** The bitting a stored text reads back as. */
    public static Bitting read(String stored) {
        return stored == null ? null : Bitting.parse(stored);
    }

    @Override
    public String convertToDatabaseColumn(Bitting attribute) {
        return bind(attribute);
    }

    @Override
    public Bitting convertToEntityAttribute(String dbData) {
        return read(dbData);
    }
}
