package com.spendly.accountservice.repository;

import com.spendly.accountservice.domain.Account;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AccountRepository extends JpaRepository<Account, UUID> {

    @Query("SELECT a FROM Account a WHERE a.id = :id AND a.ownerId = :ownerId AND a.deletedAt IS NULL")
    Optional<Account> findOwned(@Param("ownerId") UUID ownerId, @Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.id = :id AND a.ownerId = :ownerId AND a.deletedAt IS NULL")
    Optional<Account> findOwnedForUpdate(@Param("ownerId") UUID ownerId, @Param("id") UUID id);

    @Query("SELECT a FROM Account a WHERE a.ownerId = :ownerId AND a.deletedAt IS NULL"
            + " AND (:includeArchived = TRUE OR a.archivedAt IS NULL)")
    Page<Account> findAllOwned(
            @Param("ownerId") UUID ownerId, @Param("includeArchived") boolean includeArchived, Pageable pageable);
}
