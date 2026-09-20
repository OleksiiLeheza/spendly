package com.spendly.transactionservice.repository;

import com.spendly.transactionservice.domain.Transaction;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    @EntityGraph(attributePaths = "splits")
    @Query("SELECT t FROM Transaction t WHERE t.id = :id AND t.ownerId = :ownerId AND t.deletedAt IS NULL")
    Optional<Transaction> findOwned(@Param("ownerId") UUID ownerId, @Param("id") UUID id);

    @EntityGraph(attributePaths = "splits")
    @Query("SELECT t FROM Transaction t WHERE t.ownerId = :ownerId AND t.idempotencyKey = :key AND t.deletedAt IS NULL")
    Optional<Transaction> findOwnedByIdempotencyKey(@Param("ownerId") UUID ownerId, @Param("key") String key);

    @Query("SELECT COUNT(t) FROM Transaction t WHERE t.ownerId = :ownerId AND t.categoryId = :categoryId")
    long countByCategory(@Param("ownerId") UUID ownerId, @Param("categoryId") UUID categoryId);

    @Modifying
    @Query("UPDATE Transaction t SET t.categoryId = :target WHERE t.ownerId = :ownerId AND t.categoryId = :source")
    int recategorise(@Param("ownerId") UUID ownerId, @Param("source") UUID source, @Param("target") UUID target);

    @Modifying
    @Query(value = "UPDATE transaction_splits s SET category_id = :target FROM transactions t"
            + " WHERE s.transaction_id = t.id AND t.owner_id = :ownerId AND s.category_id = :source", nativeQuery = true)
    int recategoriseSplits(@Param("ownerId") UUID ownerId, @Param("source") UUID source, @Param("target") UUID target);
}
