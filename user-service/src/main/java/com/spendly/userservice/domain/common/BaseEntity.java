package com.spendly.userservice.domain.common;

import com.spendly.common.id.UuidV7Generator;
import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import java.util.Objects;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.proxy.HibernateProxy;

@Getter
@Setter
@MappedSuperclass
public abstract class BaseEntity {

    @Id
    @UuidGenerator(algorithm = UuidV7Generator.class)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Override
    public final boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null) {
            return false;
        }
        Class<?> thisType = persistentClassOf(this);
        Class<?> otherType = persistentClassOf(other);
        if (!thisType.equals(otherType)) {
            return false;
        }
        UUID thisId = getId();
        return thisId != null && thisId.equals(((BaseEntity) other).getId());
    }

    @Override
    public final int hashCode() {
        return Objects.hash(getClass().getSimpleName());
    }

    private static Class<?> persistentClassOf(Object candidate) {
        return candidate instanceof HibernateProxy proxy
                ? proxy.getHibernateLazyInitializer().getPersistentClass()
                : candidate.getClass();
    }
}
