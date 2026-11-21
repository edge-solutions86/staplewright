package io.github.rkaushikdev.staplewright.api;

import io.github.rkaushikdev.staplewright.persistence.Session;
import io.github.rkaushikdev.staplewright.settings.ShopSettings;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * One session per request, committed when the handler's work returns and rolled back when it
 * throws.
 *
 * <p>A handler does all of its work inside one call, so every service it builds in that call
 * shares the one session, and a request that used two services commits both or neither. The commit
 * happens before the response is written, so a failed commit never follows a success.
 */
public final class Work {

    private final Shop shop;

    public Work(Shop shop) {
        this.shop = shop;
    }

    public Shop shop() {
        return shop;
    }

    public ShopSettings settings() {
        return shop.settings();
    }

    /** What {@code work} answers, in a session of its own that commits when it returns. */
    public <T> T apply(Function<Session, T> work) {
        try (Session session = shop.database().session()) {
            T answer = work.apply(session);
            session.commit();
            return answer;
        }
    }

    /** {@code work} in a session of its own that commits when it returns. */
    public void run(Consumer<Session> work) {
        apply(session -> {
            work.accept(session);
            return null;
        });
    }
}
