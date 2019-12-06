package com.netflix.spinnaker.fiat.permissions;

import com.netflix.spinnaker.fiat.model.resources.Permissions;
import javax.annotation.Nonnull;

/**
 * Resolve permissions. This is useful if you do not have a way to configure a particular permission
 * and so want to apply one permission group type to another.
 */
public interface FallbackPermissionsResolver {

  /**
   * Determine if resolving fallback permissions is necessary - typically checking if permissions
   * are restricted.
   *
   * @param permissions
   * @return boolean
   */
  boolean shouldResolve(@Nonnull Permissions permissions);

  /**
   * Resolve fallback permissions.
   *
   * @param permissions
   * @return The resolved Permissions
   */
  Permissions resolve(@Nonnull Permissions permissions);
}
