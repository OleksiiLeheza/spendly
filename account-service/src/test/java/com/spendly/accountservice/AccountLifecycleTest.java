package com.spendly.accountservice;

import static com.spendly.accountservice.TestOwners.asOwner;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class AccountLifecycleTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void opensAnAccountAtZeroBalanceInItsCurrency() throws Exception {
        UUID owner = UUID.randomUUID();

        String body = mvc.perform(post("/api/v1/accounts")
                .with(asOwner(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "  Main checking ", "type": "BANK", "currency": "eur"}
                        """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("/api/v1/accounts/")))
                .andExpect(jsonPath("$.name").value("Main checking"))
                .andExpect(jsonPath("$.type").value("BANK"))
                .andExpect(jsonPath("$.currency").value("EUR"))
                .andExpect(jsonPath("$.balance.amount").value("0.00"))
                .andExpect(jsonPath("$.balance.currency").value("EUR"))
                .andExpect(jsonPath("$.archivedAt").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString();

        UUID id = UUID.fromString(JsonPath.read(body, "$.id"));
        assertThat(id.version()).isEqualTo(7);
    }

    @Test
    void rejectsAnUnknownCurrencyAsUnprocessable() throws Exception {
        mvc.perform(post("/api/v1/accounts")
                .with(asOwner(UUID.randomUUID()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "Wallet", "type": "CASH", "currency": "ZZZ"}
                        """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("'ZZZ' is not a known ISO-4217 currency"));
    }

    @Test
    void rejectsAMalformedRequestWithFieldErrors() throws Exception {
        mvc.perform(post("/api/v1/accounts")
                .with(asOwner(UUID.randomUUID()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "", "type": "CASH", "currency": "US"}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors.currency").value("must be a three-letter ISO-4217 code"));
    }

    @Test
    void listsOnlyTheCallersOwnAccounts() throws Exception {
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        open(alice, "Alice cash", "CASH", "USD");
        open(alice, "Alice bank", "BANK", "USD");
        UUID bobsAccount = open(bob, "Bob cash", "CASH", "USD");

        mvc.perform(get("/api/v1/accounts").with(asOwner(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[*].name")
                        .value(org.hamcrest.Matchers.contains("Alice cash", "Alice bank")));

        mvc.perform(get("/api/v1/accounts/{id}", bobsAccount).with(asOwner(alice)))
                .andExpect(status().isNotFound());

        mvc.perform(get("/api/v1/accounts/{id}", bobsAccount).with(asOwner(bob)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Bob cash"));
    }

    @Test
    void archivingHidesTheAccountFromTheDefaultListingButKeepsIt() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID id = open(owner, "Old card", "CREDIT_CARD", "GBP");

        mvc.perform(post("/api/v1/accounts/{id}/archive", id).with(asOwner(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archivedAt").isNotEmpty());

        mvc.perform(post("/api/v1/accounts/{id}/archive", id).with(asOwner(owner)))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/accounts").with(asOwner(owner)))
                .andExpect(jsonPath("$.totalElements").value(0));

        mvc.perform(get("/api/v1/accounts").param("includeArchived", "true").with(asOwner(owner)))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].balance.amount").value("0.00"));
    }

    @Test
    void refusesAnonymousCallers() throws Exception {
        mvc.perform(get("/api/v1/accounts")).andExpect(status().isUnauthorized());
    }

    @Test
    void publishesTheEndpointsAndTheMoneyShapeInOpenApi() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/accounts'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/accounts'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/accounts/{accountId}'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/accounts/{accountId}/archive'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/accounts/{accountId}/balance/reconcile'].post").exists())
                .andExpect(jsonPath("$.components.schemas.Money.properties.amount.type").value("string"))
                .andExpect(jsonPath("$.components.schemas.Money.properties.currency.type").value("string"))
                .andExpect(jsonPath("$.components.schemas.AccountResponse.properties.balance.$ref")
                        .value("#/components/schemas/Money"));
    }

    private UUID open(UUID owner, String name, String type, String currency) throws Exception {
        String body = mvc.perform(post("/api/v1/accounts")
                .with(asOwner(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"%s\", \"type\": \"%s\", \"currency\": \"%s\"}"
                        .formatted(name, type, currency)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.id"));
    }
}
