package dev.higor.trackflow.api.identity.application;

import static java.util.stream.Collectors.toUnmodifiableMap;

import dev.higor.trackflow.api.identity.UserDirectory;
import dev.higor.trackflow.api.identity.domain.DisplayName;
import dev.higor.trackflow.api.identity.domain.UserRepository;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class UserDirectoryService implements UserDirectory {

    private final UserRepository users;

    UserDirectoryService(UserRepository users) {
        this.users = users;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, String> displayNames(Set<UUID> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return users.findDisplayNames(userIds).stream()
                .collect(toUnmodifiableMap(DisplayName::userId, DisplayName::displayName));
    }
}
