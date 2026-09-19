package com.spendly.events;

import java.time.Instant;
import java.util.UUID;

/**
 * The envelope every published event shares.
 *
 * <p>
 * {@link #eventId()} is what makes consumers idempotent: a redelivered event carries the same id, so a consumer that
 * has already seen it can drop it (FI-48).
 */
public interface DomainEvent {

    /** Unique per event instance, stable across redelivery. */
    UUID eventId();

    /** The aggregate this event is about; also the partition key. */
    UUID aggregateId();

    /** When the fact happened, as recorded by the producing service. */
    Instant occurredAt();

    /**
     * Schema version of this payload. Topics evolve under BACKWARD compatibility (FI-45), so consumers use this to tell
     * an old shape from a new one.
     */
    default int schemaVersion() {
        return 1;
    }
}
