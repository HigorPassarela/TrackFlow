package dev.higor.trackflow.api.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import dev.higor.trackflow.api.identity.domain.AuthProvider;
import dev.higor.trackflow.api.identity.domain.User;
import dev.higor.trackflow.api.identity.domain.UserFixtures;
import dev.higor.trackflow.api.identity.domain.UserRepository;
import dev.higor.trackflow.api.shared.error.NotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-21T22:00:00Z");
    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-00000000000a");

    @Mock
    private UserRepository users;

    private AccountService service;

    @BeforeEach
    void setUp() {
        service = new AccountService(users, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("Primeiro login: cria a conta com nome e e-mail do provedor")
    void createsAccountOnFirstLogin() {
        given(users.findByProviderAndProviderSubject(AuthProvider.GITHUB, "42")).willReturn(Optional.empty());
        given(users.save(any(User.class))).willAnswer(invocation -> invocation.getArgument(0));

        User user = service.provision(githubProfile("42", "produtor@example.com"));

        assertThat(user.getProvider()).isEqualTo(AuthProvider.GITHUB);
        assertThat(user.getProviderSubject()).isEqualTo("42");
        assertThat(user.getDisplayName()).isEqualTo("Produtora A");
        assertThat(user.getEmail()).isEqualTo("produtor@example.com");
        assertThat(user.getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("Login recorrente: usa a conta existente e não cria outra")
    void reusesAccountOnRecurringLogin() {
        User existing = UserFixtures.githubUser("42");
        given(users.findByProviderAndProviderSubject(AuthProvider.GITHUB, "42")).willReturn(Optional.of(existing));

        User user = service.provision(githubProfile("42", "produtor@example.com"));

        assertThat(user).isSameAs(existing);
        then(users).should(never()).save(any());
    }

    @Test
    @DisplayName("Mesmo e-mail em provedores diferentes: cria conta separada")
    void createsSeparateAccountForSameEmailOnAnotherProvider() {
        given(users.findByProviderAndProviderSubject(AuthProvider.GOOGLE, "g-7"))
                .willReturn(Optional.empty());
        given(users.save(any(User.class))).willAnswer(invocation -> invocation.getArgument(0));

        User user = service.provision(
                new ProviderProfile(AuthProvider.GOOGLE, "g-7", "produtor@example.com", "Produtora A", null, null));

        assertThat(user.getProvider()).isEqualTo(AuthProvider.GOOGLE);
        assertThat(user.getProviderSubject()).isEqualTo("g-7");
        then(users).should(never()).findByProviderAndProviderSubject(AuthProvider.GITHUB, "42");
    }

    @Test
    void renamesExistingAccount() {
        User existing = UserFixtures.githubUser("42");
        given(users.findById(USER)).willReturn(Optional.of(existing));

        AccountView view = service.rename(USER, "VERXT");

        assertThat(view.displayName()).isEqualTo("VERXT");
        assertThat(existing.getDisplayName()).isEqualTo("VERXT");
    }

    @Test
    void reportsMissingAccountAsNotFound() {
        given(users.findById(USER)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.profile(USER))
                .isInstanceOfSatisfying(
                        NotFoundException.class,
                        error -> assertThat(error.code()).isEqualTo("user-not-found"));
    }

    @Test
    void fallsBackToLoginWhenProviderHasNoName() {
        var profile = new ProviderProfile(AuthProvider.GITHUB, "42", null, null, "verxt", null);

        assertThat(AccountService.initialDisplayName(profile)).isEqualTo("verxt");
    }

    @Test
    void fallsBackToEmailLocalPartWhenThereIsNoNameNorLogin() {
        var profile = new ProviderProfile(AuthProvider.GOOGLE, "g-7", "dj.someone@example.com", " ", null, null);

        assertThat(AccountService.initialDisplayName(profile)).isEqualTo("dj.someone");
    }

    @Test
    void usesGenericNameWhenProviderGivesNothingUsable() {
        var profile = new ProviderProfile(AuthProvider.GOOGLE, "g-7", null, "x", null, null);

        assertThat(AccountService.initialDisplayName(profile)).isEqualTo(AccountService.FALLBACK_DISPLAY_NAME);
    }

    @Test
    void truncatesNamesLongerThanSixtyCharacters() {
        var profile = new ProviderProfile(AuthProvider.GITHUB, "42", null, "a".repeat(80), null, null);

        assertThat(AccountService.initialDisplayName(profile)).hasSize(60);
    }

    private static ProviderProfile githubProfile(String subject, String email) {
        return new ProviderProfile(AuthProvider.GITHUB, subject, email, "Produtora A", "produtora-a", null);
    }
}
