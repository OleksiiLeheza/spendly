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
    private final UserEvents events;

    public ProvisioningWriter(UserRepository users, UserEvents events) {
        this.users = users;
        this.events = events;
    }

    public boolean exists(UUID subject) {
        return users.findActiveBySubject(subject).isPresent();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public User insert(CurrentUserPrincipal principal, String email) {
        User user = new User();
        user.setSubject(principal.id());
        user.setEmail(email);
        user.setDisplayName(principal.username());
        user.setRole(roleOf(principal));
        User saved = users.saveAndFlush(user);
        events.profileChanged(saved);
        return saved;
    }

    @Transactional
    public User sync(UUID subject, CurrentUserPrincipal principal, String email) {
        User user = users.findActiveBySubject(subject)
                .orElseThrow(() -> new ApiException.NotFound("User", subject));
        if (!email.equalsIgnoreCase(user.getEmail())) {
            user.setEmail(email);
            events.profileChanged(user);
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
