package com.spendly.userservice.service;

import com.spendly.common.security.CurrentUserPrincipal;
import com.spendly.common.web.ApiException;
import com.spendly.userservice.domain.User;
import com.spendly.userservice.domain.enums.Role;
import com.spendly.userservice.repository.UserRepository;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional(readOnly = true)
public class ProvisioningWriter {

    private final UserRepository users;

    public ProvisioningWriter(UserRepository users) {
        this.users = users;
    }

    public boolean exists(UUID keycloakId) {
        return users.findActiveByKeycloakId(keycloakId).isPresent();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public User insert(CurrentUserPrincipal principal, String email) {
        User user = new User();
        user.setKeycloakId(principal.id());
        user.setEmail(email);
        user.setDisplayName(principal.username());
        user.setRole(roleOf(principal));
        return users.saveAndFlush(user);
    }

    @Transactional
    public User sync(UUID keycloakId, CurrentUserPrincipal principal, String email) {
        User user = users.findActiveByKeycloakId(keycloakId)
                .orElseThrow(() -> new ApiException.NotFound("User", keycloakId));
        if (!email.equalsIgnoreCase(user.getEmail())) {
            user.setEmail(email);
        }
        Role tokenRole = roleOf(principal);
        if (user.getRole() != tokenRole) {
            user.setRole(tokenRole);
        }
        return user;
    }

    private static Role roleOf(CurrentUserPrincipal principal) {
        return principal.hasRole("ADMIN") ? Role.ADMIN : Role.USER;
    }
}
