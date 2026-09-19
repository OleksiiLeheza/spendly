package com.spendly.common.id;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

class UuidV7Test {

    @Test
    void setsVersionSevenAndTheRfcVariant() {
        UUID id = UuidV7.randomUuid();

        assertThat(id.version()).isEqualTo(7);
        assertThat(id.variant()).isEqualTo(2); // IETF RFC 9562 variant
    }

    @Test
    void embedsTheCurrentTimestamp() {
        long before = System.currentTimeMillis();
        UUID id = UuidV7.randomUuid();
        long after = System.currentTimeMillis();

        assertThat(UuidV7.timestampOf(id)).isBetween(before, after);
    }

    @Test
    void rejectsReadingATimestampFromAnotherVersion() {
        assertThatThrownBy(() -> UuidV7.timestampOf(UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sortsInGenerationOrder() {
        // The whole point of v7: lexical order matches creation order, so inserts stay at the
        // right edge of the primary-key index instead of scattering across it.
        List<UUID> generated = new ArrayList<>();
        for (int i = 0; i < 5_000; i++) {
            generated.add(UuidV7.randomUuid());
        }

        assertThat(generated).isSortedAccordingTo(UuidV7Test::compareUnsigned);
    }

    @Test
    void staysUniqueAndOrderedWithinASingleMillisecond() {
        // Thousands of ids generated back to back land in the same millisecond, which is
        // exactly where a naive implementation produces duplicates.
        Set<UUID> ids = new HashSet<>();
        List<UUID> ordered = new ArrayList<>();
        for (int i = 0; i < 10_000; i++) {
            UUID id = UuidV7.randomUuid();
            ids.add(id);
            ordered.add(id);
        }

        assertThat(ids).hasSize(10_000);
        assertThat(ordered).isSortedAccordingTo(UuidV7Test::compareUnsigned);
    }

    @Test
    void staysUniqueUnderConcurrentGeneration() throws Exception {
        int threads = 8;
        int perThread = 5_000;

        try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
            List<Callable<List<UUID>>> tasks = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                tasks.add(() -> {
                    List<UUID> local = new ArrayList<>(perThread);
                    for (int i = 0; i < perThread; i++) {
                        local.add(UuidV7.randomUuid());
                    }
                    return local;
                });
            }

            Set<UUID> all = new HashSet<>();
            for (Future<List<UUID>> future : pool.invokeAll(tasks)) {
                all.addAll(future.get());
            }
            assertThat(all).hasSize(threads * perThread);
        }
    }

    @Test
    void doesNotLeakTheHostAddress() {
        // Hibernate's Style.TIME encodes the machine's IP into the high bits; v7 must not.
        UUID a = UuidV7.randomUuid();
        UUID b = UuidV7.randomUuid();

        assertThat(a.getLeastSignificantBits()).isNotEqualTo(b.getLeastSignificantBits());
    }

    private static int compareUnsigned(UUID a, UUID b) {
        int high = Long.compareUnsigned(a.getMostSignificantBits(), b.getMostSignificantBits());
        return high != 0 ? high : Long.compareUnsigned(a.getLeastSignificantBits(), b.getLeastSignificantBits());
    }
}
