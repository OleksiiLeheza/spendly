package com.spendly.common.id;

import java.util.UUID;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.id.uuid.UuidValueGenerator;

/**
 * Hibernate hook for {@link UuidV7}.
 *
 * <p>
 * Use with {@code @UuidGenerator(algorithm = UuidV7Generator.class)}. Hibernate 7 still offers only {@code AUTO},
 * {@code RANDOM} and {@code TIME} styles, none of which is v7.
 */
public class UuidV7Generator implements UuidValueGenerator {

    @Override
    public UUID generateUuid(SharedSessionContractImplementor session) {
        return UuidV7.randomUuid();
    }
}
