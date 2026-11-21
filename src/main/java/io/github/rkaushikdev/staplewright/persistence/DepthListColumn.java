package io.github.rkaushikdev.staplewright.persistence;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A run of chamber numbers or depths, stored comma separated.
 *
 * <p>Used for the small ordered lists that are always read whole and never queried into:
 * progressed chambers, ball chambers, wafer thicknesses. Anything that wants a where clause gets its
 * own table instead. Not applied on its own: a row names it on the columns that use it.
 */
@Converter
public final class DepthListColumn implements AttributeConverter<List<Integer>, String> {

    /** How wide the column is. */
    public static final int WIDTH = 96;

    /** The stored text for a run of whole numbers, nothing for nothing. */
    public static String bind(List<Integer> value) {
        if (value == null) {
            return null;
        }
        return value.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    /** The run a stored text reads back as; an empty text is an empty run. */
    public static List<Integer> read(String stored) {
        if (stored == null) {
            return null;
        }
        if (stored.isEmpty()) {
            return List.of();
        }
        return Arrays.stream(stored.split(",", -1)).map(Integer::parseInt).toList();
    }

    @Override
    public String convertToDatabaseColumn(List<Integer> attribute) {
        return bind(attribute);
    }

    @Override
    public List<Integer> convertToEntityAttribute(String dbData) {
        return read(dbData);
    }
}
