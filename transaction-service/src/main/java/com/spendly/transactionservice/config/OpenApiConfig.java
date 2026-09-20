package com.spendly.transactionservice.config;

import com.spendly.common.money.Money;
import com.spendly.common.security.CurrentUser;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    static {
        SpringDocUtils.getConfig().addAnnotationsToIgnore(CurrentUser.class);
        SpringDocUtils.getConfig().replaceWithClass(Money.class, MoneySchema.class);
    }

    @Bean
    public OpenAPI transactionServiceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Spendly transaction-service")
                        .version("v1")
                        .description("The append-only transaction ledger and the category tree"))
                .components(new Components()
                        .addSecuritySchemes(
                                "bearer-jwt",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")));
    }
}
