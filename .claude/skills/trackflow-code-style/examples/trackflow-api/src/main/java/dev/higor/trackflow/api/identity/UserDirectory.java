package dev.higor.trackflow.api.identity;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Batch lookup of public user data for other modules. */
public interface UserDirectory {

    /**
     * Returns the artistic name of each known user in one query. Unknown ids are absent from the
     * map; callers should treat that as a deleted account.
     */
    Map<UUID, String> displayNames(Set<UUID> userIds);
}
