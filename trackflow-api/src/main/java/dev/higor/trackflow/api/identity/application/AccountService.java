package dev.higor.trackflow.api.identity.application;

import dev.higor.trackflow.api.identity.domain.User;
import dev.higor.trackflow.api.identity.domain.UserRepository;
import dev.higor.trackflow.api.shared.error.NotFoundException;
import java.time.Clock;
import java.util.UUID;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountService.class);

    static final String FALLBACK_DISPLAY_NAME = "producer";

    private final UserRepository users;
    private final Clock clock;

    public AccountService(UserRepository users, Clock clock) {
        this.users = users;
        this.clock = clock;
    }

    /**
     * Returns the account of this provider identity, creating it on the first login. Accounts are
     * never matched by e-mail: the same e-mail on another provider is another account.
     */
    @Transactional
    public User provision(ProviderProfile profile) {
        return users.findByProviderAndProviderSubject(profile.provider(), profile.subject())
                .orElseGet(() -> create(profile));
    }

    @Transactional(readOnly = true)
    public AccountView profile(UUID userId) {
        return AccountView.of(require(userId));
    }

    @Transactional
    public AccountView rename(UUID userId, String displayName) {
        User user = require(userId);
        user.rename(displayName);
        log.info("Display name changed userId={}", userId);
        return AccountView.of(user);
    }

    private User require(UUID userId) {
        return users.findById(userId).orElseThrow(() -> NotFoundException.of("user", userId));
    }

    private User create(ProviderProfile profile) {
        User user = users.save(User.builder()
                .provider(profile.provider())
                .providerSubject(profile.subject())
                .email(profile.email())
                .displayName(initialDisplayName(profile))
                .avatarUrl(profile.avatarUrl())
                .createdAt(clock.instant())
                .build());
        log.info("Account created userId={} provider={}", user.getId(), user.getProvider());
        return user;
    }

    /** The provider's name, else the login, else the e-mail's local part; always 2 to 60 characters. */
    static String initialDisplayName(ProviderProfile profile) {
        @Nullable
        String emailLocalPart = profile.email() == null ? null : profile.email().split("@", 2)[0];
        return Stream.of(profile.name(), profile.login(), emailLocalPart)
                .filter(candidate -> candidate != null && candidate.strip().length() >= User.MIN_DISPLAY_NAME_LENGTH)
                .map(candidate -> truncate(candidate.strip()))
                .findFirst()
                .orElse(FALLBACK_DISPLAY_NAME);
    }

    private static String truncate(String name) {
        return name.length() <= User.MAX_DISPLAY_NAME_LENGTH
                ? name
                : name.substring(0, User.MAX_DISPLAY_NAME_LENGTH).strip();
    }
}
