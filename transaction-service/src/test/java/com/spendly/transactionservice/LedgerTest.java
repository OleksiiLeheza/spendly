package com.spendly.transactionservice;

import static com.spendly.transactionservice.TestOwners.asOwner;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class LedgerTest extends AbstractIntegrationTest {

    private static final UUID ACCOUNT = UUID.randomUUID();
    private static final UUID SAVINGS = UUID.randomUUID();

    @Autowired
    private MockMvc mvc;

    private LedgerApi api;

    @BeforeEach
    void setUp() {
        api = new LedgerApi(mvc);
    }

    @Test
    void postsAnExpenseAndDerivesItsSign() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID groceries = api.seededCategory(owner, "food.groceries");

        String body = api.postTransaction(owner, """
                {"accountId": "%s", "type": "EXPENSE", "amount": {"amount": "42.5", "currency": "USD"},
                 "bookedOn": "2026-09-19", "description": "  Weekly shop ", "merchant": "Silpo",
                 "tags": ["Food", "food", " weekly "], "categoryId": "%s"}
                """
                .formatted(ACCOUNT, groceries))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", Matchers.containsString("/api/v1/transactions/")))
                .andExpect(jsonPath("$.description").value("Weekly shop"))
                .andExpect(jsonPath("$.amount.amount").value("42.50"))
                .andExpect(jsonPath("$.signedAmount.amount").value("-42.50"))
                .andExpect(jsonPath("$.tags").value(Matchers.contains("food", "weekly")))
                .andExpect(jsonPath("$.categoryId").value(groceries.toString()))
                .andExpect(jsonPath("$.splits").isEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(UUID.fromString(JsonPath.read(body, "$.id")).version()).isEqualTo(7);
    }

    @Test
    void replaysAnIdempotentPostInsteadOfDoubleBooking() throws Exception {
        UUID owner = UUID.randomUUID();
        String json = """
                {"accountId": "%s", "type": "INCOME", "amount": {"amount": "1000.00", "currency": "USD"},
                 "bookedOn": "2026-09-01", "description": "salary"}
                """
                .formatted(ACCOUNT);

        String first = mvc.perform(post("/api/v1/transactions")
                .with(asOwner(owner))
                .header("Idempotency-Key", "salary-2026-09")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String replay = mvc.perform(post("/api/v1/transactions")
                .with(asOwner(owner))
                .header("Idempotency-Key", "salary-2026-09")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat((String) JsonPath.read(replay, "$.id")).isEqualTo(JsonPath.read(first, "$.id"));

        mvc.perform(post("/api/v1/transactions")
                .with(asOwner(UUID.randomUUID()))
                .header("Idempotency-Key", "salary-2026-09")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isCreated());
    }

    @Test
    void rejectsEntriesThatBreakTheDomainRules() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID groceries = api.seededCategory(owner, "food.groceries");
        UUID salary = api.seededCategory(owner, "income.salary");

        api.postTransaction(owner, entry("TRANSFER", "10.00", null, null))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("A transfer names the receiving account; other types do not"));
        api.postTransaction(owner, entry("TRANSFER", "10.00", ACCOUNT, null))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("A transfer needs two different accounts"));
        api.postTransaction(owner, entry("TRANSFER", "10.00", SAVINGS, groceries))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("A transfer is not categorised"));
        api.postTransaction(owner, entry("EXPENSE", "-10.00", null, groceries))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("The amount must be positive; the type carries the direction"));
        api.postTransaction(owner, entry("EXPENSE", "10.00", null, salary))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(Matchers.containsString("INCOME category")));
        api.postTransaction(owner, entry("EXPENSE", "10.00", null, UUID.randomUUID()))
                .andExpect(status().isNotFound());
        api.postTransaction(owner, """
                {"accountId": "%s", "type": "EXPENSE", "amount": {"amount": "10.00", "currency": "XYZ"},
                 "bookedOn": "2026-09-19", "description": "x"}
                """
                .formatted(ACCOUNT))
                .andExpect(status().isBadRequest());
        api.postTransaction(owner, "{\"accountId\": \"" + ACCOUNT + "\", \"type\": \"EXPENSE\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.amount").exists())
                .andExpect(jsonPath("$.errors.bookedOn").exists())
                .andExpect(jsonPath("$.errors.description").exists());
    }

    @Test
    void splitsMustSumToTheAmountAndReplaceTheCategory() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID groceries = api.seededCategory(owner, "food.groceries");
        UUID pharmacy = api.seededCategory(owner, "health.pharmacy");

        api.postTransaction(owner, split(groceries, "10.00", pharmacy, "5.00", "20.00"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(
                        jsonPath("$.detail").value("Splits sum to USD 15.00 but the transaction amount is USD 20.00"));

        String body = api.postTransaction(owner, split(groceries, "12.50", pharmacy, "7.50", "20.00"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.categoryId").doesNotExist())
                .andExpect(jsonPath("$.splits[0].categoryId").value(groceries.toString()))
                .andExpect(jsonPath("$.splits[0].amount.amount").value("12.50"))
                .andExpect(jsonPath("$.splits[1].amount.amount").value("7.50"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        UUID id = UUID.fromString(JsonPath.read(body, "$.id"));

        mvc.perform(patch("/api/v1/transactions/{id}", id)
                .with(asOwner(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"categoryId\": \"" + groceries + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoryId").value(groceries.toString()))
                .andExpect(jsonPath("$.splits").isEmpty());

        mvc.perform(patch("/api/v1/transactions/{id}", id)
                .with(asOwner(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"clearCategory\": true, \"description\": \"relabelled\", \"tags\": [\"a\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoryId").doesNotExist())
                .andExpect(jsonPath("$.description").value("relabelled"))
                .andExpect(jsonPath("$.tags[0]").value("a"))
                .andExpect(jsonPath("$.amount.amount").value("20.00"));
    }

    @Test
    void reversalIsTheOppositeDirectionOnTheSameLabelsAndLinksBothWays() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID groceries = api.seededCategory(owner, "food.groceries");
        UUID original = api.expense(owner, ACCOUNT, "33.00", "2026-09-05", "double charged", groceries);

        String reversal = mvc.perform(post("/api/v1/transactions/{id}/reverse", original)
                .with(asOwner(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\": \"merchant refunded\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("INCOME"))
                .andExpect(jsonPath("$.signedAmount.amount").value("33.00"))
                .andExpect(jsonPath("$.bookedOn").value("2026-09-05"))
                .andExpect(jsonPath("$.description").value("Reversal of: double charged"))
                .andExpect(jsonPath("$.notes").value("merchant refunded"))
                .andExpect(jsonPath("$.categoryId").value(groceries.toString()))
                .andExpect(jsonPath("$.reversesTransactionId").value(original.toString()))
                .andReturn()
                .getResponse()
                .getContentAsString();
        UUID reversalId = UUID.fromString(JsonPath.read(reversal, "$.id"));

        mvc.perform(get("/api/v1/transactions/{id}", original).with(asOwner(owner)))
                .andExpect(jsonPath("$.reversedByTransactionId").value(reversalId.toString()));

        mvc.perform(post("/api/v1/transactions/{id}/reverse", original).with(asOwner(owner)))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/v1/transactions/{id}/reverse", reversalId).with(asOwner(owner)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void reversingATransferSwapsTheAccounts() throws Exception {
        UUID owner = UUID.randomUUID();
        String transfer = api.postTransaction(owner, entry("TRANSFER", "250.00", SAVINGS, null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.signedAmount.amount").value("-250.00"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        UUID id = UUID.fromString(JsonPath.read(transfer, "$.id"));

        mvc.perform(post("/api/v1/transactions/{id}/reverse", id).with(asOwner(owner)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("TRANSFER"))
                .andExpect(jsonPath("$.accountId").value(SAVINGS.toString()))
                .andExpect(jsonPath("$.transferAccountId").value(ACCOUNT.toString()));
    }

    @Test
    void anotherOwnersEntriesAreNotFoundNotForbidden() throws Exception {
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        UUID entry = api.expense(alice, ACCOUNT, "9.99", "2026-09-19", "alice only", null);

        mvc.perform(get("/api/v1/transactions/{id}", entry).with(asOwner(bob))).andExpect(status().isNotFound());
        mvc.perform(patch("/api/v1/transactions/{id}", entry)
                .with(asOwner(bob))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"description\": \"hijacked\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/transactions/{id}/reverse", entry).with(asOwner(bob)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/transactions/{id}", entry).with(asOwner(alice)))
                .andExpect(jsonPath("$.description").value("alice only"))
                .andExpect(jsonPath("$.reversedByTransactionId").doesNotExist());
        mvc.perform(get("/api/v1/transactions")).andExpect(status().isUnauthorized());
    }

    @Test
    void publishesTheEndpointsInOpenApi() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/transactions'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/transactions'].get.parameters[?(@.name == 'cursor')]").exists())
                .andExpect(jsonPath("$.paths['/api/v1/transactions/{transactionId}/reverse'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/categories/{categoryId}/merge'].post").exists())
                .andExpect(jsonPath("$.components.schemas.Money.properties.amount.type").value("string"));
    }

    private static String entry(String type, String amount, UUID transferAccount, UUID category) {
        return """
                {"accountId": "%s", "transferAccountId": %s, "type": "%s", "amount": {"amount": "%s", "currency": "USD"},
                 "bookedOn": "2026-09-19", "description": "entry", "categoryId": %s}
                """
                .formatted(ACCOUNT, LedgerApi.quote(transferAccount), type, amount, LedgerApi.quote(category));
    }

    private static String split(UUID first, String firstAmount, UUID second, String secondAmount, String total) {
        return """
                {"accountId": "%s", "type": "EXPENSE", "amount": {"amount": "%s", "currency": "USD"},
                 "bookedOn": "2026-09-19", "description": "split purchase",
                 "splits": [{"categoryId": "%s", "amount": {"amount": "%s", "currency": "USD"}, "note": "food"},
                            {"categoryId": "%s", "amount": {"amount": "%s", "currency": "USD"}}]}
                """
                .formatted(ACCOUNT, total, first, firstAmount, second, secondAmount);
    }
}
