package com.spendly.common.id;

import java.security.SecureRandom;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Generates RFC 9562 version 7 UUIDs: a 48-bit Unix millisecond timestamp followed by random bits.
 *
 * <p>
 * Why not the alternatives:
 * <ul>
 * <li>A random v4 scatters every insert across the primary-key B-tree, so the index never stays in cache and pages
 * fragment as the table grows.
 * <li>Hibernate's {@code Style.TIME} is a version 1 variant that encodes the host's IP address into the high bits - it
 * leaks infrastructure detail into public identifiers.
 * </ul>
 *
 * <p>
 * v7 sorts by creation time, so inserts land at the right edge of the index and "most recent first" queries become
 * range scans (ADR-0001, FI-12).
 *
 * <p>
 * Layout:
 *
 * <pre>
 *   unix_ts_ms (48) | ver=7 (4) | rand_a (12) | var=0b10 (2) | rand_b (62)
 * </pre>
 */
public final class UuidV7 {

    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * Guards against two UUIDs generated inside the same millisecond comparing equal or out of order: within a
     * millisecond the counter in rand_a increments instead of being re-randomised.
     */
    private static final AtomicLong LAST = new AtomicLong();

    private static final long SEQUENCE_MASK = 0xFFFL; // 12 bits of rand_a

    public static UUID randomUuid() {
        long timestamp = System.currentTimeMillis();

        long previous;
        long next;
        do {
            previous = LAST.get();
            long previousTimestamp = previous >>> 12;
            long previousSequence = previous & SEQUENCE_MASK;

            if (timestamp > previousTimestamp) {
                next = (timestamp << 12) | (RANDOM.nextInt(1 << 12) & SEQUENCE_MASK);
            } else if (previousSequence < SEQUENCE_MASK) {
                // Same millisecond (or a clock that stepped backwards): keep ordering by
                // advancing the counter rather than trusting the wall clock.
                next = previous + 1;
            } else {
                // Counter exhausted inside one millisecond; borrow from the next.
                next = ((previousTimestamp + 1) << 12);
            }
        } while (!LAST.compareAndSet(previous, next));

        long finalTimestamp = next >>> 12;
        long sequence = next & SEQUENCE_MASK;

        long mostSignificant = (finalTimestamp << 16) | (0x7L << 12) | sequence;
        long leastSignificant = (RANDOM.nextLong() & 0x3FFFFFFFFFFFFFFFL) | Long.MIN_VALUE;

        return new UUID(mostSignificant, leastSignificant);
    }

    /** The embedded creation timestamp, in Unix milliseconds. */
    public static long timestampOf(UUID uuid) {
        if (uuid.version() != 7) {
            throw new IllegalArgumentException("Not a version 7 UUID: " + uuid);
        }
        return uuid.getMostSignificantBits() >>> 16;
    }

    private UuidV7() {
    }
}
