package com.spendly.userservice;

import static org.assertj.core.api.Assertions.assertThat;

import com.spendly.common.security.CurrentUserPrincipal;
import com.spendly.userservice.domain.User;
import com.spendly.userservice.repository.UserRepository;
import com.spendly.userservice.service.UserProvisioningService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class ConcurrentProvisioningTest extends AbstractIntegrationTest {

    @Autowired
    private UserProvisioningService provisioning;

    @Autowired
    private UserRepository users;

    @AfterEach
    void cleanUp() {
        users.deleteAll();
        SecurityContextHolder.clearContext();
    }

    @Test
    void twoFirstRequestsForTheSameIdentityYieldOneRowAndBothCallersGetIt() throws Exception {
        UUID subject = UUID.randomUUID();
        CurrentUserPrincipal principal = principalFor(subject);
        int callers = 8;
        CountDownLatch everyoneReady = new CountDownLatch(callers);
        CountDownLatch go = new CountDownLatch(1);

        List<Callable<UUID>> tasks = new ArrayList<>();
        for (int i = 0; i < callers; i++) {
            tasks.add(() -> {
                SecurityContextHolder.getContext().setAuthentication(authenticationFor(subject));
                everyoneReady.countDown();
                go.await();
                try {
                    return provisioning.provision(principal).getId();
                } finally {
                    SecurityContextHolder.clearContext();
                }
            });
        }

        List<UUID> ids;
        try (ExecutorService pool = Executors.newFixedThreadPool(callers)) {
            List<Future<UUID>> futures = new ArrayList<>();
            for (Callable<UUID> task : tasks) {
                futures.add(pool.submit(task));
            }
            everyoneReady.await();
            go.countDown();
            ids = new ArrayList<>();
            for (Future<UUID> future : futures) {
                ids.add(future.get());
            }
        }

        assertThat(ids).hasSize(callers).doesNotContainNull();
        assertThat(Set.copyOf(ids)).as("every caller must see the same row").hasSize(1);
        assertThat(users.findAll()).extracting(User::getKeycloakId).containsExactly(subject);
    }

    @Test
    void secondCallForAKnownIdentityReturnsTheExistingRow() {
        UUID subject = UUID.randomUUID();
        SecurityContextHolder.getContext().setAuthentication(authenticationFor(subject));

        UUID first = provisioning.provision(principalFor(subject)).getId();
        UUID second = provisioning.provision(principalFor(subject)).getId();

        assertThat(second).isEqualTo(first);
        assertThat(users.count()).isEqualTo(1);
    }

    private static CurrentUserPrincipal principalFor(UUID subject) {
        return new CurrentUserPrincipal(subject, subject.toString(), subject + "@example.test", "tester",
                Set.of("USER"));
    }

    private static JwtAuthenticationToken authenticationFor(UUID subject) {
        Jwt jwt = Jwt.withTokenValue("test")
                .header("alg", "none")
                .subject(subject.toString())
                .claim("email", subject + "@example.test")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
        return new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }
}
