package com.spendly.transactionservice.api.mapper;

import com.spendly.transactionservice.api.dto.CategoryMergeResponse;
import com.spendly.transactionservice.api.dto.CategoryResponse;
import com.spendly.transactionservice.api.dto.CategoryTreeNode;
import com.spendly.transactionservice.api.dto.CreateCategoryRequest;
import com.spendly.transactionservice.api.dto.UpdateCategoryRequest;
import com.spendly.transactionservice.api.dto.CategoryItemResponse;
import com.spendly.transactionservice.domain.Category;
import com.spendly.transactionservice.domain.CategoryView;
import com.spendly.transactionservice.service.CategoryMerge;
import com.spendly.transactionservice.service.CreateCategory;
import com.spendly.transactionservice.service.UpdateCategory;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface CategoryMapper {

    CategoryResponse toResponse(Category category);

    CategoryResponse toResponse(CategoryView category);

    CategoryMergeResponse toResponse(CategoryMerge merge);

    CreateCategory toCommand(CreateCategoryRequest request);

    UpdateCategory toCommand(UpdateCategoryRequest request);

    default List<CategoryTreeNode> toTree(List<CategoryView> flat) {
        Map<UUID, List<CategoryView>> byParent = childrenByParent(flat);
        return roots(flat).stream().map(root -> node(root, byParent)).toList();
    }

    default List<CategoryItemResponse> toItems(List<CategoryView> flat) {
        Map<UUID, List<CategoryView>> byParent = childrenByParent(flat);
        List<CategoryItemResponse> items = new ArrayList<>();
        for (CategoryView root : roots(flat)) {
            walk(root, 0, root.name(), byParent, items);
        }
        return items;
    }

    private void walk(CategoryView category, int depth, String path, Map<UUID, List<CategoryView>> byParent,
            List<CategoryItemResponse> into) {
        into.add(new CategoryItemResponse(toResponse(category), depth, path));
        for (CategoryView child : byParent.getOrDefault(category.id(), List.of())) {
            walk(child, depth + 1, path + " \u203a " + child.name(), byParent, into);
        }
    }

    private static List<CategoryView> roots(List<CategoryView> flat) {
        return flat.stream().filter(category -> category.parentId() == null).toList();
    }

    private static Map<UUID, List<CategoryView>> childrenByParent(List<CategoryView> flat) {
        Map<UUID, List<CategoryView>> byParent = new LinkedHashMap<>();
        for (CategoryView category : flat) {
            if (category.parentId() != null) {
                byParent.computeIfAbsent(category.parentId(), key -> new ArrayList<>()).add(category);
            }
        }
        return byParent;
    }

    private CategoryTreeNode node(CategoryView category, Map<UUID, List<CategoryView>> byParent) {
        List<CategoryTreeNode> children = byParent.getOrDefault(category.id(), List.of()).stream()
                .map(child -> node(child, byParent))
                .toList();
        return new CategoryTreeNode(toResponse(category), children);
    }
}
