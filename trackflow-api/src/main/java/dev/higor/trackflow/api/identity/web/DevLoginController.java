package dev.higor.trackflow.api.identity.web;

import dev.higor.trackflow.api.identity.application.DevAccounts;
import dev.higor.trackflow.api.identity.application.ProvisioningOAuth2UserService;
import dev.higor.trackflow.api.identity.domain.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Signs in without an OAuth provider so the project runs with no external accounts. The bean only
 * exists with the "local" profile; anywhere else the route answers 404 like any unknown path.
 */
@RestController
@Profile("local")
class DevLoginController {

    private static final Logger log = LoggerFactory.getLogger(DevLoginController.class);

    private final DevAccounts devAccounts;
    private final SecurityContextRepository contextRepository = new HttpSessionSecurityContextRepository();

    DevLoginController(DevAccounts devAccounts) {
        this.devAccounts = devAccounts;
    }

    /** Leaves the same kind of principal in the session as the OAuth2 login does. */
    @PostMapping("/api/dev/login")
    DevLoginResponse login(
            @Valid @RequestBody DevLoginRequest body, HttpServletRequest request, HttpServletResponse response) {
        User user = devAccounts.signIn(body.name());
        OAuth2User principal = ProvisioningOAuth2UserService.principalOf(user);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new OAuth2AuthenticationToken(principal, principal.getAuthorities(), "dev"));
        SecurityContextHolder.setContext(context);
        if (request.getSession(false) != null) {
            request.changeSessionId(); // no session fixation, even in development
        }
        contextRepository.saveContext(context, request, response);
        log.info("Development login userId={}", user.getId());
        return new DevLoginResponse(user.getId(), user.getDisplayName());
    }

    record DevLoginResponse(UUID id, String displayName) {}
}
