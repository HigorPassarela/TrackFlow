package dev.higor.trackflow.api.identity.application;

import dev.higor.trackflow.api.identity.AuthenticatedUser;
import dev.higor.trackflow.api.identity.domain.AuthProvider;
import dev.higor.trackflow.api.identity.domain.User;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;

/**
 * Picked up by {@code oauth2Login()} because it is the only {@link OAuth2UserService} bean. Turns
 * the provider's user into our account and keeps in the session only what {@link
 * AuthenticatedUser} needs, never the provider's raw attributes.
 */
@Component
public class ProvisioningOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private final AccountService accounts;
    private final OAuth2UserService<OAuth2UserRequest, OAuth2User> providerUsers = new DefaultOAuth2UserService();

    ProvisioningOAuth2UserService(AccountService accounts) {
        this.accounts = accounts;
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest request) {
        OAuth2User providerUser = providerUsers.loadUser(request);
        ProviderProfile profile =
                profileOf(request.getClientRegistration().getRegistrationId(), providerUser.getAttributes());
        return principalOf(accounts.provision(profile));
    }

    static ProviderProfile profileOf(String registrationId, Map<String, Object> attributes) {
        return switch (registrationId) {
            case "github" ->
                new ProviderProfile(
                        AuthProvider.GITHUB,
                        String.valueOf(attributes.get("id")),
                        text(attributes, "email"),
                        text(attributes, "name"),
                        text(attributes, "login"),
                        text(attributes, "avatar_url"));
            case "google" ->
                new ProviderProfile(
                        AuthProvider.GOOGLE,
                        String.valueOf(attributes.get("sub")),
                        text(attributes, "email"),
                        text(attributes, "name"),
                        null,
                        text(attributes, "picture"));
            default ->
                throw new OAuth2AuthenticationException(
                        new OAuth2Error("unsupported_provider"), "Unsupported login provider " + registrationId);
        };
    }

    /** The session principal for an account; also used by the development login. */
    public static OAuth2User principalOf(User user) {
        return new DefaultOAuth2User(
                AuthorityUtils.createAuthorityList("ROLE_USER"),
                Map.of(
                        AuthenticatedUser.USER_ID_ATTRIBUTE, user.getId().toString(),
                        AuthenticatedUser.DISPLAY_NAME_ATTRIBUTE, user.getDisplayName()),
                AuthenticatedUser.USER_ID_ATTRIBUTE);
    }

    private static @Nullable String text(Map<String, Object> attributes, String key) {
        return attributes.get(key) instanceof String value && !value.isBlank() ? value : null;
    }
}
