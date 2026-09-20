package com.spendly.userservice.service;

import com.spendly.common.security.CurrentUserPrincipal;
import com.spendly.common.web.ApiException;
import com.spendly.userservice.domain.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
public class UserProvisioningService {

    private static final Logger log = LoggerFactory.getLogger(UserProvisioningService.class);

    private final ProvisioningWriter writer;

    public UserProvisioningService(ProvisioningWriter writer) {
        this.writer = writer;
    }

    @PreAuthorize("isAuthenticated()")
    public User provision(CurrentUserPrincipal principal) {
        String email = requireEmail(principal);
        if (writer.exists(principal.id())) {
            return writer.sync(principal.id(), principal, email);
        }
        try {
            User created = writer.insert(principal, email);
            log.info("Provisioned local profile for subject {}", principal.subject());
            return created;
        } catch (DataIntegrityViolationException raceLostToAConcurrentInsert) {
            log.debug("Concurrent provisioning for subject {}, adopting the winner's row", principal.subject());
            return writer.sync(principal.id(), principal, email);
        }
    }

    private static String requireEmail(CurrentUserPrincipal principal) {
        String email = principal.email();
        if (email == null || email.isBlank()) {
            throw new ApiException.Unprocessable("The access token carries no email claim; request the 'email' scope");
        }
        return email;
    }
}
