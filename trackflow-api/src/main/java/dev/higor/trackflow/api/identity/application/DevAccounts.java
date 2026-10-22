package dev.higor.trackflow.api.identity.application;

import dev.higor.trackflow.api.identity.domain.AuthProvider;
import dev.higor.trackflow.api.identity.domain.User;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/** Test accounts for the development login. The bean only exists with the "local" profile. */
@Service
@Profile("local")
public class DevAccounts {

    private final AccountService accounts;

    public DevAccounts(AccountService accounts) {
        this.accounts = accounts;
    }

    /** The same name always signs in to the same test account. */
    public User signIn(String name) {
        return accounts.provision(new ProviderProfile(AuthProvider.DEV, name, null, name, name, null));
    }
}
