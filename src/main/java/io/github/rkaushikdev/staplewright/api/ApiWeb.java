package io.github.rkaushikdev.staplewright.api;

import org.springframework.format.FormatterRegistry;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Query and path parameters naming a choice are read by the value the choice is written as. */
@Component
class ApiWeb implements WebMvcConfigurer {

    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverterFactory(new ValuedJson.Parameters());
    }
}
