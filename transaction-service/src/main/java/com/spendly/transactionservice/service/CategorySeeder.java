package com.spendly.transactionservice.service;

import com.spendly.transactionservice.domain.Category;
import com.spendly.transactionservice.repository.CategoryRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CategorySeeder {

    private static final Logger log = LoggerFactory.getLogger(CategorySeeder.class);

    private final CategoryRepository categories;
    private final CategorySeeds seeds;

    public CategorySeeder(CategoryRepository categories, CategorySeeds seeds) {
        this.categories = categories;
        this.seeds = seeds;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int seed(UUID ownerId, String language) {
        List<CategorySeed> definitions = seeds.forLanguage(language);
        List<Category> roots = new ArrayList<>();
        int order = 0;
        for (CategorySeed seed : definitions) {
            roots.add(Category.create(ownerId, seed.name(), seed.kind(), seed.icon(), null, order++, seed.key()));
        }
        roots = categories.saveAllAndFlush(roots);

        List<Category> children = new ArrayList<>();
        for (int i = 0; i < roots.size(); i++) {
            Category root = roots.get(i);
            int childOrder = 0;
            for (CategorySeed.Child child : definitions.get(i).children()) {
                children.add(Category.create(
                        ownerId, child.name(), root.getKind(), null, root.getId(), childOrder++, child.key()));
            }
        }
        categories.saveAllAndFlush(children);
        log.info("Seeded {} default categories ({}) for owner {}", roots.size() + children.size(), language, ownerId);
        return roots.size() + children.size();
    }
}
