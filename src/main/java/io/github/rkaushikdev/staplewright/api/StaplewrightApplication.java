package io.github.rkaushikdev.staplewright.api;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.github.rkaushikdev.staplewright.Staplewright;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;

/**
 * The http service, wired to one shop.
 *
 * <p>The service never builds a database of its own: it is handed a {@link Shop} and every request
 * opens its session there. Boot's own data source, mapping and migration set up is switched off for
 * that reason; the schema is built with {@code staplewright build}, not by starting the service.
 */
@SpringBootApplication(
        exclude = {
            DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            FlywayAutoConfiguration.class
        })
public class StaplewrightApplication {

    /** The service on this shop. */
    public static ConfigurableApplicationContext run(Shop shop, String... args) {
        return run(shop, List.of(), args);
    }

    /** The service on this shop, with extra configuration or routes alongside its own. */
    public static ConfigurableApplicationContext run(Shop shop, List<Class<?>> extra, String... args) {
        List<Class<?>> sources = new ArrayList<>(List.of(StaplewrightApplication.class));
        sources.addAll(extra);
        SpringApplication application = new SpringApplicationBuilder(sources.toArray(Class<?>[]::new))
                .initializers(context -> context.getBeanFactory().registerSingleton("shop", shop))
                .build();
        return application.run(args);
    }

    @Bean
    Work work(Shop shop) {
        return new Work(shop);
    }

    /** Request bodies read their fields in snake case, and days travel as ISO text. */
    @Bean
    Jackson2ObjectMapperBuilderCustomizer houseJson() {
        return builder -> builder.propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    /** Choices read and written as the values they are written as; Boot installs every module bean. */
    @Bean
    com.fasterxml.jackson.databind.Module valuedChoices() {
        return new ValuedJson();
    }

    @Bean
    OpenAPI openApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("staplewright")
                        .version(Staplewright.VERSION)
                        .description("key control and master key system design for the lock shop"));
    }
}
