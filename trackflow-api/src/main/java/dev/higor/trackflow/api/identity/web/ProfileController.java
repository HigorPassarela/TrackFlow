package dev.higor.trackflow.api.identity.web;

import dev.higor.trackflow.api.identity.AuthenticatedUser;
import dev.higor.trackflow.api.identity.application.AccountService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me")
class ProfileController {

    private final AccountService accounts;

    ProfileController(AccountService accounts) {
        this.accounts = accounts;
    }

    @GetMapping
    ProfileResponse get(AuthenticatedUser user) {
        return ProfileResponse.from(accounts.profile(user.id()));
    }

    @PatchMapping
    ProfileResponse update(@Valid @RequestBody UpdateProfileRequest request, AuthenticatedUser user) {
        return ProfileResponse.from(accounts.rename(user.id(), request.displayName()));
    }
}
