package com.spendly.transactionservice;

import static com.spendly.transactionservice.TestOwners.asOwner;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@AutoConfigureMockMvc
class TransactionFeedTest extends AbstractIntegrationTest {

    private static final UUID CHECKING = UUID.randomUUID();
    private static final UUID SAVINGS = UUID.randomUUID();

    @Autowired
    private MockMvc mvc;

    private LedgerApi api;

    @BeforeEach
    void setUp() {
        api = new LedgerApi(mvc);
    }

    @Test
    void pagesNewestFirstWithoutGapsOrDuplicates() throws Exception {
        UUID owner = UUID.randomUUID();
        List<UUID> posted = new ArrayList<>();
        for (int day = 1; day <= 4; day++) {
            posted.add(api.expense(owner, CHECKING, "1.00", "2026-09-0" + day, "entry " + day + "a", null));
            posted.add(api.expense(owner, CHECKING, "1.00", "2026-09-0" + day, "entry " + day + "b", null));
        }

        List<String> seen = new ArrayList<>();
        String cursor = null;
        int pages = 0;
        do {
            String page = feed(owner, cursor, "limit", "3");
            List<String> ids = JsonPath.read(page, "$.items[*].id");
            List<String> dates = JsonPath.read(page, "$.items[*].bookedOn");
            assertThat(ids).hasSizeLessThanOrEqualTo(3);
            assertThat(dates).isSortedAccordingTo(java.util.Comparator.reverseOrder());
            seen.addAll(ids);
            cursor = JsonPath.parse(page).read("$.nextCursor", String.class);
            pages++;
        } while (cursor != null);

        assertThat(pages).isEqualTo(3);
        assertThat(seen).doesNotHaveDuplicates().hasSize(8);
        assertThat(seen.stream().map(UUID::fromString)).containsExactlyInAnyOrderElementsOf(posted);
        assertThat(seen.getFirst()).isEqualTo(posted.getLast().toString());
    }

    @Test
    void filtersByAccountTypeDateRangeAndCategory() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID groceries = api.seededCategory(owner, "food.groceries");
        UUID pharmacy = api.seededCategory(owner, "health.pharmacy");
        UUID shop = api.expense(owner, CHECKING, "10.00", "2026-09-02", "shop", groceries);
        UUID split = UUID.fromString(JsonPath.read(
                api.postTransaction(owner, """
                        {"accountId": "%s", "type": "EXPENSE", "amount": {"amount": "20.00", "currency": "USD"},
                         "bookedOn": "2026-09-03", "description": "mixed",
                         "splits": [{"categoryId": "%s", "amount": {"amount": "5.00", "currency": "USD"}},
                                    {"categoryId": "%s", "amount": {"amount": "15.00", "currency": "USD"}}]}
                        """
                        .formatted(CHECKING, groceries, pharmacy))
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.id"));
        UUID transfer = UUID.fromString(JsonPath.read(
                api.postTransaction(owner,
                        """
                                {"accountId": "%s", "transferAccountId": "%s", "type": "TRANSFER",
                                 "amount": {"amount": "100.00", "currency": "USD"}, "bookedOn": "2026-09-04", "description": "to savings"}
                                """
                                .formatted(CHECKING, SAVINGS))
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.id"));
        UUID old = api.expense(owner, SAVINGS, "1.00", "2026-08-30", "old", null);

        assertThat(ids(feed(owner, null, "accountId", SAVINGS.toString())))
                .containsExactly(transfer.toString(), old.toString());
        assertThat(ids(feed(owner, null, "type", "TRANSFER"))).containsExactly(transfer.toString());
        assertThat(ids(feed(owner, null, "from", "2026-09-01", "to", "2026-09-02"))).containsExactly(shop.toString());
        assertThat(ids(feed(owner, null, "categoryId", groceries.toString())))
                .containsExactly(split.toString(), shop.toString());
        assertThat(ids(feed(owner, null, "categoryId", pharmacy.toString()))).containsExactly(split.toString());
        assertThat((Boolean) JsonPath.read(feed(owner, null, "categoryId", pharmacy.toString()), "$.items[0].split"))
                .isTrue();
    }

    @Test
    void searchesDescriptionMerchantAndNotesInAnyLanguage() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID coffee = api.expense(owner, CHECKING, "3.50", "2026-09-10", "Morning coffee", null, "Blue Bottle", null);
        UUID bread = api.expense(owner, CHECKING, "2.00", "2026-09-11", "Bakery", null, "Silpo",
                "sourdough bread for the week");
        UUID kyiv = api.expense(owner, CHECKING, "12.00", "2026-09-12", "Обід у Києві", null, "Пузата Хата", null);
        api.expense(owner, CHECKING, "1.00", "2026-09-13", "unrelated", null);

        assertThat(ids(feed(owner, null, "q", "coffee"))).containsExactly(coffee.toString());
        assertThat(ids(feed(owner, null, "q", "blue bottle"))).containsExactly(coffee.toString());
        assertThat(ids(feed(owner, null, "q", "sourdough"))).containsExactly(bread.toString());
        assertThat(ids(feed(owner, null, "q", "Києві"))).containsExactly(kyiv.toString());
        assertThat(ids(feed(owner, null, "q", "coffee OR bakery"))).containsExactly(bread.toString(),
                coffee.toString());
        assertThat(ids(feed(owner, null, "q", "silpo -bread"))).isEmpty();
        assertThat(ids(feed(owner, null, "q", "nothing-like-this"))).isEmpty();
    }

    @Test
    void rejectsAForeignCursorAndAnOversizedPage() throws Exception {
        UUID owner = UUID.randomUUID();
        mvc.perform(get("/api/v1/transactions").param("cursor", "bm90LWEtY3Vyc29y").with(asOwner(owner)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("The cursor is not one this feed issued"));
        mvc.perform(get("/api/v1/transactions").param("limit", "201").with(asOwner(owner)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void neverShowsAnotherOwnersEntries() throws Exception {
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        api.expense(alice, CHECKING, "5.00", "2026-09-10", "alice coffee", null);

        assertThat(ids(feed(bob, null, "q", "coffee"))).isEmpty();
        assertThat(ids(feed(bob, null, "accountId", CHECKING.toString()))).isEmpty();
    }

    private String feed(UUID owner, @Nullable String cursor, String... params) throws Exception {
        MockHttpServletRequestBuilder request = get("/api/v1/transactions").with(asOwner(owner));
        if (cursor != null) {
            request.param("cursor", cursor);
        }
        for (int i = 0; i < params.length; i += 2) {
            request.param(params[i], params[i + 1]);
        }
        return mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private static List<String> ids(String page) {
        return JsonPath.read(page, "$.items[*].id");
    }
}
