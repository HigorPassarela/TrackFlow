package dev.higor.trackflow.api.identity;

import java.util.UUID;

/**
 * The logged-in user as controllers see it. Resolved from the session by the identity module, so
 * controllers declare it as a plain parameter and never touch Spring Security types.
 */
public record AuthenticatedUser(UUID id, String displayName) {

    /** Principal attribute where the identity module stores our user id after the OAuth2 login. */
    public static final String USER_ID_ATTRIBUTE = "trackflow_user_id";

    /** Principal attribute with the artistic name at login time. */
    public static final String DISPLAY_NAME_ATTRIBUTE = "trackflow_display_name";
}
