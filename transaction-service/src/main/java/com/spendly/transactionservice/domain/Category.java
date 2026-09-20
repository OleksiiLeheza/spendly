package com.spendly.transactionservice.domain;

import com.spendly.common.persistence.BaseEntityAudit;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.jspecify.annotations.Nullable;

@Entity
@Getter
@Setter
@Table(name = "categories")
public class Category extends BaseEntityAudit {

    @Column(name = "owner_id", nullable = false, updatable = false)
    private UUID ownerId;

    @Column(name = "parent_id")
    private @Nullable UUID parentId;

    @Column(name = "name", nullable = false, length = 80)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, updatable = false, length = 10)
    private CategoryKind kind;

    @Column(name = "icon", length = 40)
    private @Nullable String icon;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "seed_key", length = 60, updatable = false)
    private @Nullable String seedKey;

    @Column(name = "archived_at")
    private @Nullable Instant archivedAt;

    @Column(name = "merged_into_id")
    private @Nullable UUID mergedIntoId;

    public static Category create(
            UUID ownerId,
            String name,
            CategoryKind kind,
            @Nullable String icon,
            @Nullable UUID parentId,
            int sortOrder,
            @Nullable String seedKey) {
        Category category = new Category();
        category.ownerId = ownerId;
        category.name = name;
        category.kind = kind;
        category.icon = icon;
        category.parentId = parentId;
        category.sortOrder = sortOrder;
        category.seedKey = seedKey;
        return category;
    }

    public boolean isArchived() {
        return archivedAt != null;
    }

    public boolean isRoot() {
        return parentId == null;
    }

    public void archive(Instant at) {
        if (archivedAt == null) {
            archivedAt = at;
        }
    }

    public void mergeInto(Category target, Instant at) {
        archive(at);
        mergedIntoId = target.getId();
    }
}
