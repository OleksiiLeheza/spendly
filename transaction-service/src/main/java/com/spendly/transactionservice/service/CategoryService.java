package com.spendly.transactionservice.service;

import com.spendly.common.web.ApiException;
import com.spendly.transactionservice.domain.Category;
import com.spendly.transactionservice.domain.CategoryKind;
import com.spendly.transactionservice.domain.CategorySnapshot;
import com.spendly.transactionservice.domain.CategoryView;
import com.spendly.transactionservice.repository.CategoryRepository;
import com.spendly.transactionservice.repository.TransactionRepository;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@PreAuthorize("isAuthenticated()")
public class CategoryService {

    static final int MAX_DEPTH = 3;
    static final int MAX_PER_OWNER = 500;

    private static final Logger log = LoggerFactory.getLogger(CategoryService.class);

    private final CategoryRepository categories;
    private final TransactionRepository transactions;
    private final CategorySeeds seeds;
    private final CategorySeeder seeder;
    private final CategoryReads reads;
    private final Clock clock;
    private final LedgerEvents events;

    public CategoryService(
            CategoryRepository categories,
            TransactionRepository transactions,
            CategorySeeds seeds,
            CategorySeeder seeder,
            CategoryReads reads,
            Clock clock,
            LedgerEvents events) {
        this.categories = categories;
        this.transactions = transactions;
        this.seeds = seeds;
        this.seeder = seeder;
        this.reads = reads;
        this.clock = clock;
        this.events = events;
    }

    @Transactional
    public List<CategoryView> tree(UUID ownerId, Locale locale, boolean includeArchived) {
        if (categories.countSeeded(ownerId) == 0) {
            try {
                seeder.seed(ownerId, seeds.languageFor(locale));
            } catch (DataIntegrityViolationException ex) {
                log.debug("Concurrent seeding for owner {}, keeping the existing set", ownerId);
            }
            reads.evict(ownerId);
        }
        CategorySnapshot snapshot = reads.all(ownerId);
        return includeArchived ? snapshot.categories() : snapshot.live();
    }

    public Category get(UUID ownerId, UUID categoryId) {
        return owned(ownerId, categoryId);
    }

    @Transactional
    public Category create(UUID ownerId, CreateCategory command) {
        if (categories.countOwned(ownerId) >= MAX_PER_OWNER) {
            throw new ApiException.Unprocessable("At most " + MAX_PER_OWNER + " categories per owner");
        }
        Category parent = command.parentId() == null ? null : liveParent(ownerId, command.parentId(), command.kind());
        int depth = parent == null ? 1 : depthOf(ownerId, parent) + 1;
        if (depth > MAX_DEPTH) {
            throw new ApiException.Unprocessable("Categories nest at most " + MAX_DEPTH + " levels deep");
        }
        Category category = Category.create(
                ownerId, command.name().strip(), command.kind(), blankToNull(command.icon()), command.parentId(), 0,
                null);
        Category saved = saveUnique(category);
        events.categoryChanged(saved);
        reads.evict(ownerId);
        return saved;
    }

    @Transactional
    public Category update(UUID ownerId, UUID categoryId, UpdateCategory command) {
        Category category = owned(ownerId, categoryId);
        if (category.isArchived()) {
            throw new ApiException.Conflict("Category " + categoryId + " is archived");
        }
        if (command.name() != null) {
            category.setName(command.name().strip());
        }
        if (command.icon() != null) {
            category.setIcon(blankToNull(command.icon()));
        }
        if (command.sortOrder() != null) {
            category.setSortOrder(command.sortOrder());
        }
        if (command.movesToRoot()) {
            category.setParentId(null);
        } else if (command.parentId() != null) {
            reparent(ownerId, category, command.parentId());
        }
        categories.flush();
        events.categoryChanged(category);
        reads.evict(ownerId);
        return category;
    }

    @Transactional
    public Category archive(UUID ownerId, UUID categoryId) {
        Category category = owned(ownerId, categoryId);
        category.archive(clock.instant());
        events.categoryChanged(category);
        for (Category child : categories.findChildren(ownerId, categoryId)) {
            child.archive(clock.instant());
            events.categoryChanged(child);
        }
        reads.evict(ownerId);
        return category;
    }

    @Transactional
    public CategoryMerge merge(UUID ownerId, UUID sourceId, UUID targetId) {
        if (sourceId.equals(targetId)) {
            throw new ApiException.Unprocessable("A category cannot be merged into itself");
        }
        Category source = owned(ownerId, sourceId);
        Category target = owned(ownerId, targetId);
        if (target.isArchived()) {
            throw new ApiException.Unprocessable("Cannot merge into an archived category");
        }
        if (source.getKind() != target.getKind()) {
            throw new ApiException.Unprocessable(
                    "Cannot merge " + source.getKind() + " category into " + target.getKind() + " category");
        }
        if (ancestorsOf(ownerId, target).contains(sourceId)) {
            throw new ApiException.Unprocessable("Cannot merge a category into its own descendant");
        }

        int transactionsMoved = transactions.recategorise(ownerId, sourceId, targetId);
        int splitsMoved = transactions.recategoriseSplits(ownerId, sourceId, targetId);
        int childrenMoved = categories.reparentChildren(ownerId, sourceId, targetId);
        source.mergeInto(target, clock.instant());
        events.categoryChanged(source);
        reads.evict(ownerId);
        events.merged(ownerId, sourceId, targetId);
        log.info("Merged category {} into {} for owner {}", sourceId, targetId, ownerId);
        return new CategoryMerge(sourceId, targetId, transactionsMoved, splitsMoved, childrenMoved);
    }

    private Category saveUnique(Category category) {
        try {
            return categories.saveAndFlush(category);
        } catch (DataIntegrityViolationException ex) {
            throw new ApiException.Conflict(
                    "A category named '" + category.getName() + "' already exists at this level");
        }
    }

    private void reparent(UUID ownerId, Category category, UUID newParentId) {
        if (newParentId.equals(category.getId())) {
            throw new ApiException.Unprocessable("A category cannot be its own parent");
        }
        Category parent = liveParent(ownerId, newParentId, category.getKind());
        if (ancestorsOf(ownerId, parent).contains(category.getId())) {
            throw new ApiException.Unprocessable("Moving a category under its own descendant would form a cycle");
        }
        if (depthOf(ownerId, parent) + 1 + subtreeDepth(ownerId, category) > MAX_DEPTH) {
            throw new ApiException.Unprocessable("Categories nest at most " + MAX_DEPTH + " levels deep");
        }
        category.setParentId(newParentId);
    }

    private Category liveParent(UUID ownerId, UUID parentId, CategoryKind kind) {
        Category parent = owned(ownerId, parentId);
        if (parent.isArchived()) {
            throw new ApiException.Unprocessable("Parent category " + parentId + " is archived");
        }
        if (parent.getKind() != kind) {
            throw new ApiException.Unprocessable(
                    "A " + kind + " category cannot sit under a " + parent.getKind() + " one");
        }
        return parent;
    }

    private int depthOf(UUID ownerId, Category category) {
        return ancestorsOf(ownerId, category).size() + 1;
    }

    private List<UUID> ancestorsOf(UUID ownerId, Category category) {
        List<UUID> ancestors = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        UUID parentId = category.getParentId();
        while (parentId != null && seen.add(parentId)) {
            ancestors.add(parentId);
            parentId = owned(ownerId, parentId).getParentId();
        }
        return ancestors;
    }

    private int subtreeDepth(UUID ownerId, Category category) {
        int deepest = 0;
        for (Category child : categories.findChildren(ownerId, category.getId())) {
            deepest = Math.max(deepest, 1 + subtreeDepth(ownerId, child));
        }
        return deepest;
    }

    private Category owned(UUID ownerId, UUID categoryId) {
        return categories.findOwned(ownerId, categoryId)
                .orElseThrow(() -> new ApiException.NotFound("Category", categoryId));
    }

    private static @Nullable String blankToNull(@Nullable String value) {
        if (value == null) {
            return null;
        }
        String stripped = value.strip();
        return stripped.isEmpty() ? null : stripped;
    }
}
