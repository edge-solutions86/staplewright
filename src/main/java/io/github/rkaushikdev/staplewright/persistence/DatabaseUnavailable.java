package io.github.rkaushikdev.staplewright.persistence;

import io.github.rkaushikdev.staplewright.Facts;
import io.github.rkaushikdev.staplewright.StaplewrightError;
import java.sql.SQLException;

/** The database would not answer: the URL is wrong, the file is locked, or the server is down. */
public final class DatabaseUnavailable extends StaplewrightError {

    public DatabaseUnavailable(String url, SQLException cause) {
        super("the database would not answer", Facts.of("url", url, "reason", String.valueOf(cause.getMessage())));
        initCause(cause);
    }
}
