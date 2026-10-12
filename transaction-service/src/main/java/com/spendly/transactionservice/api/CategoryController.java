package com.spendly.transactionservice.api;

import com.spendly.common.security.CurrentUser;
import com.spendly.common.security.CurrentUserPrincipal;
import com.spendly.transactionservice.api.dto.CategoryMergeResponse;
import com.spendly.transactionservice.api.dto.CategoryResponse;
import com.spendly.transactionservice.api.dto.CategoryTreeResponse;
import com.spendly.transactionservice.api.dto.CreateCategoryRequest;
import com.spendly.transactionservice.api.dto.MergeCategoryRequest;
import com.spendly.transactionservice.api.dto.UpdateCategoryRequest;
import com.spendly.transactionservice.api.mapper.CategoryMapper;
import com.spendly.transactionservice.domain.Category;
import com.spendly.transactionservice.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping(value = "/api/v1/categories", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Categories", description = "The caller's category tree. Defaults are seeded in the caller's language on first use.")
@SecurityRequirement(name = "bearer-jwt")
public class CategoryController {

    private final CategoryService categories;
    private final CategoryMapper mapper;

    public CategoryController(CategoryService categories, CategoryMapper mapper) {
        this.categories = categories;
        this.mapper = mapper;
    }

    @GetMapping(version = "v1")
    @Operation(operationId = "listCategories", summary = "The whole tree, nested", description = "On the caller's first call the defaults are seeded from the Accept-Language header (en, uk). Version 2 of this endpoint returns the same categories as one ordered list.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The tree"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid access token", content = {})
    })
    public CategoryTreeResponse tree(
            @CurrentUser CurrentUserPrincipal principal,
            @Parameter(hidden = true) Locale locale,
            @Parameter(description = "Also return archived categories") @RequestParam(defaultValue = "false") boolean includeArchived) {
        return new CategoryTreeResponse(mapper.toTree(categories.tree(principal.id(), locale, includeArchived)));
    }

    @PostMapping
    @Operation(operationId = "createCategory", summary = "Create a category")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "The new category"),
            @ApiResponse(responseCode = "400", description = "Validation failed", content = {}),
            @ApiResponse(responseCode = "401", description = "Missing or invalid access token", content = {}),
            @ApiResponse(responseCode = "404", description = "No such parent for this caller", content = {}),
            @ApiResponse(responseCode = "409", description = "A sibling with that name exists", content = {}),
            @ApiResponse(responseCode = "422", description = "Nesting or kind rule broken", content = {})
    })
    public ResponseEntity<CategoryResponse> create(
            @CurrentUser CurrentUserPrincipal principal, @Valid @RequestBody CreateCategoryRequest request) {
        Category category = categories.create(principal.id(), mapper.toCommand(request));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(category.getId())
                .toUri();
        return ResponseEntity.created(location).body(mapper.toResponse(category));
    }

    @GetMapping("/{categoryId}")
    @Operation(operationId = "getCategory", summary = "Get one category")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The category"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid access token", content = {}),
            @ApiResponse(responseCode = "404", description = "No such category for this caller", content = {})
    })
    public CategoryResponse get(@CurrentUser CurrentUserPrincipal principal, @PathVariable UUID categoryId) {
        return mapper.toResponse(categories.get(principal.id(), categoryId));
    }

    @PatchMapping("/{categoryId}")
    @Operation(operationId = "updateCategory", summary = "Rename, re-icon, reorder or move a category")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The updated category"),
            @ApiResponse(responseCode = "400", description = "Validation failed", content = {}),
            @ApiResponse(responseCode = "401", description = "Missing or invalid access token", content = {}),
            @ApiResponse(responseCode = "404", description = "No such category or parent for this caller", content = {}),
            @ApiResponse(responseCode = "409", description = "Archived, or a sibling with that name exists", content = {}),
            @ApiResponse(responseCode = "422", description = "The move would form a cycle or nest too deep", content = {})
    })
    public CategoryResponse update(
            @CurrentUser CurrentUserPrincipal principal,
            @PathVariable UUID categoryId,
            @Valid @RequestBody UpdateCategoryRequest request) {
        return mapper.toResponse(categories.update(principal.id(), categoryId, mapper.toCommand(request)));
    }

    @PostMapping("/{categoryId}/archive")
    @Operation(operationId = "archiveCategory", summary = "Archive a category and its children", description = "Existing entries keep the label; new ones cannot use it. Idempotent.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The archived category"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid access token", content = {}),
            @ApiResponse(responseCode = "404", description = "No such category for this caller", content = {})
    })
    public CategoryResponse archive(@CurrentUser CurrentUserPrincipal principal, @PathVariable UUID categoryId) {
        return mapper.toResponse(categories.archive(principal.id(), categoryId));
    }

    @PostMapping("/{categoryId}/merge")
    @Operation(operationId = "mergeCategory", summary = "Merge this category into another", description = "Relabels every entry and split, moves the children under the target, then archives this category.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "What moved"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid access token", content = {}),
            @ApiResponse(responseCode = "404", description = "No such category for this caller", content = {}),
            @ApiResponse(responseCode = "422", description = "Same category, different kinds, archived target or a cycle", content = {})
    })
    public CategoryMergeResponse merge(
            @CurrentUser CurrentUserPrincipal principal,
            @PathVariable UUID categoryId,
            @Valid @RequestBody MergeCategoryRequest request) {
        return mapper.toResponse(categories.merge(principal.id(), categoryId, request.targetId()));
    }
}
