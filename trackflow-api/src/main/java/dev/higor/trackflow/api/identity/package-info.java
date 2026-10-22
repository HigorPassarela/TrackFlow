/**
 * Who uses Trackflow: accounts created on the first social login, the artistic name, the session
 * and the local-only development login.
 *
 * <p>Public API: {@link dev.higor.trackflow.api.identity.AuthenticatedUser} and {@link
 * dev.higor.trackflow.api.identity.UserDirectory}. Everything in sub-packages is internal.
 */
@ApplicationModule(displayName = "Identity")
package dev.higor.trackflow.api.identity;

import org.springframework.modulith.ApplicationModule;
