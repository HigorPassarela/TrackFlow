/**
 * Shared kernel: error model, web helpers, time and token utilities used by every module.
 *
 * <p>Open module on purpose. It must never depend on another module: anything that knows about
 * projects, versions or users belongs somewhere else.
 */
@ApplicationModule(displayName = "Shared kernel", type = ApplicationModule.Type.OPEN)
package dev.higor.trackflow.api.shared;

import org.springframework.modulith.ApplicationModule;
