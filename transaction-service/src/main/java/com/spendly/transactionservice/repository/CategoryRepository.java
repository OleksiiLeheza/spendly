package com.spendly.transactionservice.repository;

import com.spendly.transactionservice.domain.Category;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CategoryRepository extends JpaRepository<Category, UUID> {

    @Query("SELECT c FROM Category c WHERE c.id = :id AND c.ownerId = :ownerId AND c.deletedAt IS NULL")
    Optional<Category> findOwned(@Param("ownerId") UUID ownerId, @Param("id") UUID id);

    @Query("SELECT c FROM Category c WHERE c.ownerId = :ownerId AND c.id IN :ids AND c.deletedAt IS NULL")
    List<Category> findAllOwned(@Param("ownerId") UUID ownerId, @Param("ids") Collection<UUID> ids);

    @Query("SELECT c FROM Category c WHERE c.ownerId = :ownerId AND c.deletedAt IS NULL"
            + " AND (:includeArchived = TRUE OR c.archivedAt IS NULL) ORDER BY c.sortOrder, c.name")
    List<Category> findTree(@Param("ownerId") UUID ownerId, @Param("includeArchived") boolean includeArchived);

    @Query("SELECT c FROM Category c WHERE c.ownerId = :ownerId AND c.parentId = :parentId AND c.deletedAt IS NULL")
    List<Category> findChildren(@Param("ownerId") UUID ownerId, @Param("parentId") UUID parentId);

    @Query("SELECT COUNT(c) FROM Category c WHERE c.ownerId = :ownerId AND c.seedKey IS NOT NULL")
    long countSeeded(@Param("ownerId") UUID ownerId);

    @Query("SELECT COUNT(c) FROM Category c WHERE c.ownerId = :ownerId AND c.deletedAt IS NULL")
    long countOwned(@Param("ownerId") UUID ownerId);

    @Modifying
    @Query("UPDATE Category c SET c.parentId = :target WHERE c.ownerId = :ownerId AND c.parentId = :source")
    int reparentChildren(@Param("ownerId") UUID ownerId, @Param("source") UUID source, @Param("target") UUID target);
}
