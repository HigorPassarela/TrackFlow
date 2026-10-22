package dev.higor.trackflow.api.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.higor.trackflow.api.identity.AuthenticatedUser;
import dev.higor.trackflow.api.identity.domain.AuthProvider;
import dev.higor.trackflow.api.identity.domain.UserFixtures;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.test.util.ReflectionTestUtils;

class ProvisioningOAuth2UserServiceTest {

    @Test
    void readsGithubAttributes() {
        Map<String, Object> attributes = Map.of(
                "id", 42, "login", "verxt", "name", "VERXT", "email", "v@example.com", "avatar_url", "https://a/v.png");

        var profile = ProvisioningOAuth2UserService.profileOf("github", attributes);

        assertThat(profile)
                .isEqualTo(new ProviderProfile(
                        AuthProvider.GITHUB, "42", "v@example.com", "VERXT", "verxt", "https://a/v.png"));
    }

    @Test
    void readsGoogleAttributes() {
        Map<String, Object> attributes =
                Map.of("sub", "1098", "name", "VERXT", "email", "v@example.com", "picture", "https://g/v.png");

        var profile = ProvisioningOAuth2UserService.profileOf("google", attributes);

        assertThat(profile)
                .isEqualTo(new ProviderProfile(
                        AuthProvider.GOOGLE, "1098", "v@example.com", "VERXT", null, "https://g/v.png"));
    }

    @Test
    void rejectsUnknownProvider() {
        assertThatThrownBy(() -> ProvisioningOAuth2UserService.profileOf("myspace", Map.of("id", 1)))
                .isInstanceOf(OAuth2AuthenticationException.class);
    }

    @Test
    void keepsOnlyOurIdAndDisplayNameInTheSession() {
        var user = UserFixtures.githubUser("42");
        UUID id = UUID.fromString("00000000-0000-0000-0000-00000000000a");
        ReflectionTestUtils.setField(user, "id", id);

        var principal = ProvisioningOAuth2UserService.principalOf(user);

        assertThat(principal.getName()).isEqualTo(id.toString());
        assertThat(principal.getAttributes())
                .containsOnlyKeys(AuthenticatedUser.USER_ID_ATTRIBUTE, AuthenticatedUser.DISPLAY_NAME_ATTRIBUTE)
                .containsEntry(AuthenticatedUser.DISPLAY_NAME_ATTRIBUTE, "Produtor 42");
    }
}
