package com.spendly.transactionservice;

import static com.spendly.transactionservice.TestOwners.asOwner;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class CategoryTreeTest extends AbstractIntegrationTest {

    private static final UUID ACCOUNT = UUID.randomUUID();

    @Autowired
    private MockMvc mvc;

    private LedgerApi api;

    @BeforeEach
    void setUp() {
        api = new LedgerApi(mvc);
    }

    @Test
    void seedsEnglishDefaultsOnFirstCallAndNeverAgain() throws Exception {
        UUID owner = UUID.randomUUID();

        String first = api.tree(owner, "en-US");
        List<String> roots = JsonPath.read(first, "$.roots[*].category.name");
        assertThat(roots).startsWith("Income", "Food & drink", "Housing");
        List<String> foodChildren = JsonPath.read(first, "$.roots[1].children[*].category.name");
        assertThat(foodChildren).containsExactly("Groceries", "Restaurants & cafés", "Delivery");
        assertThat((String) JsonPath.read(first, "$.roots[1].children[0].category.seedKey"))
                .isEqualTo("food.groceries");
        assertThat((String) JsonPath.read(first, "$.roots[0].category.kind")).isEqualTo("INCOME");

        String second = api.tree(owner, "uk");
        assertThat((List<?>) JsonPath.read(second, "$..category.id"))
                .hasSameSizeAs(JsonPath.read(first, "$..category.id"));
        assertThat((List<String>) JsonPath.read(second, "$.roots[*].category.name")).startsWith("Income");
    }

    @Test
    void seedsUkrainianDefaultsWhenTheCallerSpeaksUkrainian() throws Exception {
        String tree = api.tree(UUID.randomUUID(), "uk-UA,uk;q=0.9,en;q=0.5");
        List<String> roots = JsonPath.read(tree, "$.roots[*].category.name");
        assertThat(roots).startsWith("Доходи", "Їжа та напої", "Житло");
        assertThat((String) JsonPath.read(tree, "$.roots[1].children[0].category.seedKey")).isEqualTo("food.groceries");
    }

    @Test
    void fallsBackToEnglishForAnUnsupportedLanguage() throws Exception {
        String tree = api.tree(UUID.randomUUID(), "de-DE");
        assertThat((String) JsonPath.read(tree, "$.roots[0].category.name")).isEqualTo("Income");
    }

    @Test
    void enforcesUniqueSiblingsKindsAndDepth() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID food = api.seededCategory(owner, "food");
        UUID groceries = api.seededCategory(owner, "food.groceries");

        UUID coffee = api.createCategory(owner, "Coffee", "EXPENSE", food);
        mvc.perform(post("/api/v1/categories")
                .with(asOwner(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"coffee\", \"kind\": \"EXPENSE\", \"parentId\": \"" + food + "\"}"))
                .andExpect(status().isConflict());

        mvc.perform(post("/api/v1/categories")
                .with(asOwner(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"Bonus\", \"kind\": \"INCOME\", \"parentId\": \"" + food + "\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("A INCOME category cannot sit under a EXPENSE one"));

        UUID beans = api.createCategory(owner, "Beans", "EXPENSE", coffee);
        mvc.perform(post("/api/v1/categories")
                .with(asOwner(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"Arabica\", \"kind\": \"EXPENSE\", \"parentId\": \"" + beans + "\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("Categories nest at most 3 levels deep"));

        mvc.perform(patch("/api/v1/categories/{id}", food)
                .with(asOwner(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"parentId\": \"" + coffee + "\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("Moving a category under its own descendant would form a cycle"));

        mvc.perform(patch("/api/v1/categories/{id}", groceries)
                .with(asOwner(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"Supermarket\", \"moveToRoot\": true, \"sortOrder\": 5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Supermarket"))
                .andExpect(jsonPath("$.parentId").doesNotExist())
                .andExpect(jsonPath("$.sortOrder").value(5));
    }

    @Test
    void archivingHidesTheSubtreeButKeepsItRetrievable() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID leisure = api.seededCategory(owner, "leisure");

        mvc.perform(post("/api/v1/categories/{id}/archive", leisure).with(asOwner(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archivedAt").isNotEmpty());

        String visible = api.tree(owner, "en");
        assertThat((List<?>) JsonPath.read(visible, "$..category[?(@.seedKey == 'leisure.travel')]")).isEmpty();

        String all = mvc.perform(get("/api/v1/categories").param("includeArchived", "true").with(asOwner(owner)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat((List<?>) JsonPath.read(all, "$..category[?(@.seedKey == 'leisure.travel')].archivedAt")).hasSize(1);

        api.postTransaction(owner, """
                {"accountId": "%s", "type": "EXPENSE", "amount": {"amount": "5.00", "currency": "USD"},
                 "bookedOn": "2026-09-01", "description": "cinema", "categoryId": "%s"}
                """
                .formatted(ACCOUNT, leisure))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("Category " + leisure + " is archived"));
    }

    @Test
    void mergeRelabelsEntriesAndSplitsMovesChildrenAndArchivesTheSource() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID restaurants = api.seededCategory(owner, "food.restaurants");
        UUID delivery = api.seededCategory(owner, "food.delivery");
        UUID groceries = api.seededCategory(owner, "food.groceries");
        UUID sushi = api.createCategory(owner, "Sushi", "EXPENSE", delivery);

        UUID whole = api.expense(owner, ACCOUNT, "20.00", "2026-09-10", "pizza", delivery);
        String split = api.postTransaction(owner, """
                {"accountId": "%s", "type": "EXPENSE", "amount": {"amount": "30.00", "currency": "USD"},
                 "bookedOn": "2026-09-11", "description": "mixed order",
                 "splits": [{"categoryId": "%s", "amount": {"amount": "12.50", "currency": "USD"}},
                            {"categoryId": "%s", "amount": {"amount": "17.50", "currency": "USD"}}]}
                """
                .formatted(ACCOUNT, delivery, groceries))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        UUID splitId = UUID.fromString(JsonPath.read(split, "$.id"));

        mvc.perform(post("/api/v1/categories/{id}/merge", delivery)
                .with(asOwner(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetId\": \"" + restaurants + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionsMoved").value(1))
                .andExpect(jsonPath("$.splitsMoved").value(1))
                .andExpect(jsonPath("$.childrenMoved").value(1));

        mvc.perform(get("/api/v1/transactions/{id}", whole).with(asOwner(owner)))
                .andExpect(jsonPath("$.categoryId").value(restaurants.toString()));
        mvc.perform(get("/api/v1/transactions/{id}", splitId).with(asOwner(owner)))
                .andExpect(jsonPath("$.splits[0].categoryId").value(restaurants.toString()))
                .andExpect(jsonPath("$.splits[1].categoryId").value(groceries.toString()));
        mvc.perform(get("/api/v1/categories/{id}", sushi).with(asOwner(owner)))
                .andExpect(jsonPath("$.parentId").value(restaurants.toString()));
        mvc.perform(get("/api/v1/categories/{id}", delivery).with(asOwner(owner)))
                .andExpect(jsonPath("$.archivedAt").isNotEmpty())
                .andExpect(jsonPath("$.mergedIntoId").value(restaurants.toString()));

        UUID salary = api.seededCategory(owner, "income.salary");
        mvc.perform(post("/api/v1/categories/{id}/merge", groceries)
                .with(asOwner(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetId\": \"" + salary + "\"}"))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/api/v1/categories/{id}/merge", groceries)
                .with(asOwner(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetId\": \"" + groceries + "\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void anotherOwnersCategoriesAreNotFoundNotForbidden() throws Exception {
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        UUID alicesFood = api.seededCategory(alice, "food");

        mvc.perform(get("/api/v1/categories/{id}", alicesFood).with(asOwner(bob))).andExpect(status().isNotFound());
        mvc.perform(patch("/api/v1/categories/{id}", alicesFood)
                .with(asOwner(bob))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"Mine now\"}"))
                .andExpect(status().isNotFound());
        api.postTransaction(bob, """
                {"accountId": "%s", "type": "EXPENSE", "amount": {"amount": "5.00", "currency": "USD"},
                 "bookedOn": "2026-09-01", "description": "x", "categoryId": "%s"}
                """
                .formatted(ACCOUNT, alicesFood))
                .andExpect(status().isNotFound());
        assertThat((List<?>) JsonPath.read(api.tree(bob, "en"), "$.roots"))
                .satisfies(roots -> assertThat(roots).isNotEmpty());
        mvc.perform(get("/api/v1/categories").with(asOwner(bob))).andExpect(jsonPath("$.roots", hasSize(10)));
    }
}
