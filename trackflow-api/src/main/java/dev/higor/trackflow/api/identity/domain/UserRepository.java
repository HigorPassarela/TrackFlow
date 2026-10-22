package dev.higor.trackflow.api.identity.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.ListCrudRepository;

public interface UserRepository extends ListCrudRepository<User, UUID> {

    Optional<User> findByProviderAndProviderSubject(AuthProvider provider, String providerSubject);

    /** One query for any number of ids; only the two columns callers need. */
    @Query(
            "select new dev.higor.trackflow.api.identity.domain.DisplayName(u.id, u.displayName) from User u where u.id in :ids")
    List<DisplayName> findDisplayNames(Collection<UUID> ids);
}
