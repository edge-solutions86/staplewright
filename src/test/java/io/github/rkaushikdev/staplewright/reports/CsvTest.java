package io.github.rkaushikdev.staplewright.reports;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class CsvTest {

    @Test
    void aFieldIsQuotedOnlyWhenItHasToBe() {
        assertThat(Csv.line(List.of("plain", " padded ", "a, b", "x\"y", "two\nlines")))
                .isEqualTo("plain, padded ,\"a, b\",\"x\"\"y\",\"two\nlines\"\n");
    }

    @Test
    void aLoneEmptyFieldIsWrittenAsQuotes() {
        assertThat(Csv.line(List.of(""))).isEqualTo("\"\"\n");
        assertThat(Csv.line(List.of("", ""))).isEqualTo(",\n");
    }

    @Test
    void quotedFieldsComeBackWithoutTheirQuotes() {
        assertThat(Csv.read("a,\"b,c\"\n\"x\"\"y\"z,q\n")).containsExactly(List.of("a", "b,c"), List.of("x\"yz", "q"));
    }

    @Test
    void aBlankLineIsARecordWithNoFields() {
        assertThat(Csv.read("a\n\n,\n")).containsExactly(List.of("a"), List.of(), List.of("", ""));
    }

    @Test
    void aLineBreakInsideQuotesBelongsToTheField() {
        assertThat(Csv.read("\"two\r\nlines\",b\r\nc")).containsExactly(List.of("two\r\nlines", "b"), List.of("c"));
    }

    @Test
    void aQuoteThatNeverClosesRunsToTheEnd() {
        assertThat(Csv.read("a,\"un")).containsExactly(List.of("a", "un"));
        assertThat(Csv.read("")).isEmpty();
    }
}
