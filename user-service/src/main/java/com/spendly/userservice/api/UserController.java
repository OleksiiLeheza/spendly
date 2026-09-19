package com.spendly.userservice.api;

import com.spendly.common.security.CurrentUser;
import com.spendly.common.security.CurrentUserPrincipal;
import com.spendly.userservice.api.dto.UpdateProfileRequest;
import com.spendly.userservice.api.dto.UserResponse;
import com.spendly.userservice.api.mapper.UserMapper;
import com.spendly.userservice.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Users", description = "The caller's own profile. Keycloak owns credentials and account recovery.")
@SecurityRequirement(name = "bearer-jwt")
public class UserController {

    private final UserService users;
    private final UserMapper mapper;

    public UserController(UserService users, UserMapper mapper) {
        this.users = users;
        this.mapper = mapper;
    }

    @GetMapping("/me")
    @Operation(summary = "Get the caller's profile", description = "Creates the local profile on first call, from the access-token claims.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The caller's profile"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid access token", content = {})
    })
    public UserResponse me(@CurrentUser CurrentUserPrincipal principal) {
        return mapper.toResponse(users.provision(principal));
    }

    @PatchMapping("/me")
    @Operation(summary = "Update the caller's profile")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The updated profile"),
            @ApiResponse(responseCode = "400", description = "Validation failed", content = {}),
            @ApiResponse(responseCode = "401", description = "Missing or invalid access token", content = {}),
            @ApiResponse(responseCode = "422", description = "Unknown currency", content = {})
    })
    public ResponseEntity<UserResponse> updateMe(
            @CurrentUser CurrentUserPrincipal principal, @Valid @RequestBody UpdateProfileRequest request) {

        users.provision(principal);
        return ResponseEntity.ok(
                mapper.toResponse(users.updateProfile(principal.id(), mapper.toCommand(request))));
    }
}
