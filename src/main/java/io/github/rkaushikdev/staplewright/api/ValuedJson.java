package io.github.rkaushikdev.staplewright.api;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.deser.BeanDeserializerModifier;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;
import io.github.rkaushikdev.staplewright.Valued;
import java.io.IOException;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.convert.converter.ConverterFactory;

/**
 * Choices travel as the value they are written as: a cause is {@code "lost"}, not {@code "LOST"}.
 *
 * <p>A body carrying a value no choice has is refused before it reaches a route, with the values
 * that would have been accepted, and the same holds for a query parameter.
 */
final class ValuedJson extends SimpleModule {

    ValuedJson() {
        super("valued-choices");
        setDeserializerModifier(new BeanDeserializerModifier() {
            @Override
            public JsonDeserializer<?> modifyEnumDeserializer(
                    DeserializationConfig config,
                    JavaType type,
                    BeanDescription description,
                    JsonDeserializer<?> standard) {
                return Valued.class.isAssignableFrom(type.getRawClass()) ? new Reader(type.getRawClass()) : standard;
            }
        });
        setSerializerModifier(new BeanSerializerModifier() {
            @Override
            public JsonSerializer<?> modifyEnumSerializer(
                    SerializationConfig config,
                    JavaType type,
                    BeanDescription description,
                    JsonSerializer<?> standard) {
                return Valued.class.isAssignableFrom(type.getRawClass()) ? new Writer() : standard;
            }
        });
    }

    /** The choice of {@code kind} written as {@code text}, refusing text no choice is written as. */
    static Object choose(Class<?> kind, String text) {
        for (Object choice : kind.getEnumConstants()) {
            if (String.valueOf(((Valued) choice).value()).equals(text)) {
                return choice;
            }
        }
        throw new IllegalArgumentException("Input should be " + expected(kind));
    }

    private static String expected(Class<?> kind) {
        return Arrays.stream(kind.getEnumConstants())
                .map(choice -> "'" + ((Valued) choice).value() + "'")
                .collect(Collectors.joining(", "));
    }

    private static final class Reader extends JsonDeserializer<Object> {
        private final Class<?> kind;

        Reader(Class<?> kind) {
            this.kind = kind;
        }

        @Override
        public Object deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            String text = parser.getValueAsString();
            try {
                return choose(kind, text);
            } catch (IllegalArgumentException refused) {
                return context.handleWeirdStringValue(kind, text, refused.getMessage());
            }
        }
    }

    private static final class Writer extends JsonSerializer<Object> {
        @Override
        public void serialize(Object value, JsonGenerator generator, SerializerProvider provider) throws IOException {
            generator.writeObject(((Valued) value).value());
        }
    }

    /** The same reading for query and path parameters. */
    static final class Parameters implements ConverterFactory<String, Enum<?>> {
        @Override
        @SuppressWarnings({"unchecked", "rawtypes"})
        public <T extends Enum<?>> Converter<String, T> getConverter(Class<T> kind) {
            if (!Valued.class.isAssignableFrom(kind)) {
                return text -> (T) Enum.valueOf((Class) kind, text);
            }
            return text -> kind.cast(choose(kind, text));
        }
    }
}
