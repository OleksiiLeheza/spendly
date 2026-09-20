package com.spendly.transactionservice.api.mapper;

import com.spendly.transactionservice.api.dto.CategoryMergeResponse;
import com.spendly.transactionservice.api.dto.CategoryResponse;
import com.spendly.transactionservice.api.dto.CategoryTreeNode;
import com.spendly.transactionservice.api.dto.CreateCategoryRequest;
import com.spendly.transactionservice.api.dto.UpdateCategoryRequest;
import com.spendly.transactionservice.domain.Category;
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

    CategoryMergeResponse toResponse(CategoryMerge merge);

    CreateCategory toCommand(CreateCategoryRequest request);

    UpdateCategory toCommand(UpdateCategoryRequest request);

    default List<CategoryTreeNode> toTree(List<Category> flat) {
        Map<UUID, List<Category>> byParent = new LinkedHashMap<>();
        List<Category> roots = new ArrayList<>();
        for (Category category : flat) {
            if (category.getParentId() == null) {
                roots.add(category);
            } else {
                byParent.computeIfAbsent(category.getParentId(), key -> new ArrayList<>()).add(category);
            }
        }
        return roots.stream().map(root -> node(root, byParent)).toList();
    }

    private CategoryTreeNode node(Category category, Map<UUID, List<Category>> byParent) {
        List<CategoryTreeNode> children = byParent.getOrDefault(category.getId(), List.of()).stream()
                .map(child -> node(child, byParent))
                .toList();
        return new CategoryTreeNode(toResponse(category), children);
    }
}
