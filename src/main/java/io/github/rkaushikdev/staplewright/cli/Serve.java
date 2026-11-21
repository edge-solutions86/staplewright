package io.github.rkaushikdev.staplewright.cli;

import io.github.rkaushikdev.staplewright.api.Shop;
import io.github.rkaushikdev.staplewright.api.StaplewrightApplication;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.ContextClosedEvent;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

/**
 * Run the http service on this database until it is stopped.
 *
 * <p>The service is handed the same database and settings every other command uses, so what the
 * counter does at the terminal and what a phone does over http land in one place. It does not build
 * the schema: {@code staplewright build} does that.
 */
@Command(name = "serve", mixinStandardHelpOptions = true, description = "Run the http service until it is stopped.")
final class Serve implements Callable<Integer> {

    @ParentCommand
    private Main main;

    @Option(names = "--host", defaultValue = "127.0.0.1", description = "the address to listen on")
    private String host;

    @Option(names = "--port", defaultValue = "8000", description = "the port to listen on; 0 picks a free one")
    private int port;

    @Override
    public Integer call() {
        CountDownLatch closed = new CountDownLatch(1);
        Shop shop = new Shop(main.database(), main.settings());
        try (ConfigurableApplicationContext service =
                StaplewrightApplication.run(shop, "--server.port=" + port, "--server.address=" + host)) {
            service.addApplicationListener(event -> {
                if (event instanceof ContextClosedEvent) {
                    closed.countDown();
                }
            });
            int listening = ((ServletWebServerApplicationContext) service)
                    .getWebServer()
                    .getPort();
            main.terminal().out().println("serving " + main.url() + " on http://" + host + ":" + listening);
            main.terminal().out().flush();
            if (service.isActive()) {
                closed.await();
            }
        } catch (InterruptedException stopped) {
            Thread.currentThread().interrupt();
        }
        return Main.OK;
    }
}
