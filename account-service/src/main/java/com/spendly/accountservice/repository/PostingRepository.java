package com.spendly.accountservice.repository;

import com.spendly.accountservice.domain.Posting;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PostingRepository extends JpaRepository<Posting, UUID> {

    @Query("SELECT p FROM Posting p WHERE p.ownerId = :ownerId AND p.account.id = :accountId"
            + " AND p.ledgerEntryId = :ledgerEntryId")
    Optional<Posting> findOwnedByLedgerEntry(
            @Param("ownerId") UUID ownerId,
            @Param("accountId") UUID accountId,
            @Param("ledgerEntryId") UUID ledgerEntryId);

    @Query("SELECT COALESCE(SUM(p.amount.amount), 0) FROM Posting p"
            + " WHERE p.ownerId = :ownerId AND p.account.id = :accountId")
    BigDecimal sumOwned(@Param("ownerId") UUID ownerId, @Param("accountId") UUID accountId);

    @Query("SELECT COUNT(p) FROM Posting p WHERE p.ownerId = :ownerId AND p.account.id = :accountId")
    long countOwned(@Param("ownerId") UUID ownerId, @Param("accountId") UUID accountId);
}
