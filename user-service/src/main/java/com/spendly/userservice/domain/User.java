package com.spendly.userservice.domain;

import com.spendly.common.persistence.BaseEntityAudit;
import com.spendly.userservice.domain.enums.Role;
import com.spendly.userservice.domain.enums.UserStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;

@Entity
@Getter
@Setter
@Table(name = "users")
public class User extends BaseEntityAudit {

    @Column(name = "keycloak_id", nullable = false, updatable = false, unique = true)
    private UUID keycloakId;

    @Column(name = "email", nullable = false, unique = true, length = 320)
    private String email;

    @Column(name = "first_name", length = 100)
    private @Nullable String firstName;

    @Column(name = "last_name", length = 100)
    private @Nullable String lastName;

    @Column(name = "display_name", length = 200)
    private @Nullable String displayName;

    @Column(name = "avatar_url", length = 2048)
    private @Nullable String avatarUrl;

    @Column(name = "locale", length = 35)
    private @Nullable String locale;

    @Column(name = "zone_id", length = 64)
    private @Nullable String zoneId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "default_currency", length = 3, nullable = false, columnDefinition = "char(3)")
    private String defaultCurrency = "USD";

    @Column(name = "identity_provider", length = 50)
    private @Nullable String identityProvider;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private Role role = Role.USER;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private UserStatus status = UserStatus.ACTIVE;

    public String resolvedDisplayName() {
        if (displayName != null && !displayName.isBlank()) {
            return displayName;
        }
        String full = ((firstName == null ? "" : firstName) + " " + (lastName == null ? "" : lastName)).strip();
        return full.isBlank() ? email : full;
    }
}
