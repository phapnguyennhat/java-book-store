package com.example.book_store.web;

import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.HeaderParameter;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
class OpenApiConfig {

    /** Language picker on every endpoint in Swagger UI; must list the languages that have a messages_*.properties. */
    @Bean
    OperationCustomizer acceptLanguageHeader() {
        return (operation, handlerMethod) -> operation.addParametersItem(new HeaderParameter()
                .name("Accept-Language")
                .description("Language of error messages; unsupported or missing falls back to en")
                .schema(new StringSchema()._enum(List.of("en", "vi"))._default("en")));
    }
}
