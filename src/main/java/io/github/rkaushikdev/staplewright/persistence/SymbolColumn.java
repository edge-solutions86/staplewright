package io.github.rkaushikdev.staplewright.persistence;

import io.github.rkaushikdev.staplewright.keys.KeySymbol;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * A key symbol, stored as it is printed. Storing AB3 and parsing it on the way out keeps the queries
 * legible and keeps the parsing rules in one place.
 */
@Converter(autoApply = true)
public final class SymbolColumn implements AttributeConverter<KeySymbol, String> {

    /** How wide the column is. */
    public static final int WIDTH = 16;

    /**
     * The text stored for a value: a symbol as it renders, text read as a symbol first, nothing as
     * nothing.
     *
     * @throws IllegalArgumentException for anything else
     * @throws io.github.rkaushikdev.staplewright.HierarchyError for text that is not a symbol
     */
    public static String bind(Object value) {
        return switch (value) {
            case null -> null;
            case KeySymbol symbol -> symbol.render();
            case String text -> KeySymbol.parse(text).render();
            default ->
                throw new IllegalArgumentException(
                        "cannot store " + value.getClass().getSimpleName() + " as a key symbol");
        };
    }

    /** The symbol a stored text reads back as. */
    public static KeySymbol read(String stored) {
        return stored == null ? null : KeySymbol.parse(stored);
    }

    @Override
    public String convertToDatabaseColumn(KeySymbol attribute) {
        return bind(attribute);
    }

    @Override
    public KeySymbol convertToEntityAttribute(String dbData) {
        return read(dbData);
    }
}
