/**
 * Projects (one per track), their members and roles, invites and the permission checks every
 * other module relies on.
 *
 * <p>Public API: {@link dev.higor.trackflow.api.project.ProjectAccess}, {@link
 * dev.higor.trackflow.api.project.ProjectActivity} and {@link
 * dev.higor.trackflow.api.project.ProjectRole}. Everything in sub-packages is internal.
 */
@ApplicationModule(displayName = "Projects")
package dev.higor.trackflow.api.project;

import org.springframework.modulith.ApplicationModule;
