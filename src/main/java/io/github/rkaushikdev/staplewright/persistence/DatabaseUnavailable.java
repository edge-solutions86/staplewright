package io.github.rkaushikdev.staplewright.persistence;

import java.sql.SQLException;

/**
 * The database would not answer: the URL is wrong, the file is locked, or the server is down.
 *
 * <p>This is not a refusal. Nothing the caller asked for was wrong, so it carries no refusal code;
 * it is the connection failing, with the driver's reason attached.
 */
public final class DatabaseUnavailable extends RuntimeException {

    private final String url;

    public DatabaseUnavailable(String url, SQLException cause) {
        super("the database at " + url + " would not answer: " + cause.getMessage(), cause);
        this.url = url;
    }

    /** The URL that would not answer. */
    public String url() {
        return url;
    }
}
