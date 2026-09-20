package com.spendly.transactionservice;

import static com.spendly.transactionservice.TestOwners.asOwner;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

final class LedgerApi {

    private final MockMvc mvc;

    LedgerApi(MockMvc mvc) {
        this.mvc = mvc;
    }

    String tree(UUID owner, String language) throws Exception {
        return mvc.perform(get("/api/v1/categories").with(asOwner(owner)).header("Accept-Language", language))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    UUID seededCategory(UUID owner, String seedKey) throws Exception {
        String tree = tree(owner, "en");
        List<String> ids = JsonPath.read(tree, "$..category[?(@.seedKey == '" + seedKey + "')].id");
        if (ids.isEmpty()) {
            throw new AssertionError("No seeded category " + seedKey);
        }
        return UUID.fromString(ids.getFirst());
    }

    UUID createCategory(UUID owner, String name, String kind, UUID parentId) throws Exception {
        String parent = parentId == null ? "null" : "\"" + parentId + "\"";
        String body = mvc.perform(post("/api/v1/categories")
                .with(asOwner(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"%s\", \"kind\": \"%s\", \"parentId\": %s}".formatted(name, kind, parent)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.id"));
    }

    ResultActions postTransaction(UUID owner, String json) throws Exception {
        return mvc.perform(post("/api/v1/transactions")
                .with(asOwner(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    UUID expense(UUID owner, UUID account, String amount, String bookedOn, String description, UUID category)
            throws Exception {
        return expense(owner, account, amount, bookedOn, description, category, null, null);
    }

    UUID expense(
            UUID owner,
            UUID account,
            String amount,
            String bookedOn,
            String description,
            UUID category,
            String merchant,
            String notes)
            throws Exception {
        String body = postTransaction(owner, """
                {"accountId": "%s", "type": "EXPENSE", "amount": {"amount": "%s", "currency": "USD"},
                 "bookedOn": "%s", "description": "%s", "categoryId": %s, "merchant": %s, "notes": %s}
                """
                .formatted(account, amount, bookedOn, description, quote(category), quote(merchant), quote(notes)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.id"));
    }

    static String quote(Object value) {
        return value == null ? "null" : "\"" + value + "\"";
    }
}
