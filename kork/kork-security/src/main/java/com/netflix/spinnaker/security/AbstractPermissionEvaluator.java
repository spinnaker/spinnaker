/*
 * Copyright 2023 Apple, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.netflix.spinnaker.security;

import java.io.Serializable;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.Authentication;

/**
 * Base implementation for permission evaluators that support {@link AccessControlled} domain
 * objects.
 */
public abstract class AbstractPermissionEvaluator implements PermissionEvaluator {

  @Override
  public boolean hasPermission(
      Authentication authentication, Object targetDomainObject, Object permission) {
    if (isDisabled()) {
      return true;
    }
    if (authentication == null || targetDomainObject == null) {
      return false;
    }
    if (SpinnakerAuthorities.isAdmin(authentication)) {
      return true;
    }
    if (targetDomainObject instanceof AccessControlled) {
      return ((AccessControlled) targetDomainObject).isAuthorized(authentication, permission);
    }
    return false;
  }

  @Override
  public boolean hasPermission(
      Authentication authentication, Serializable targetId, String targetType, Object permission) {
    if (isDisabled()) {
      return true;
    }
    return hasPermission(
        SpinnakerUsers.getUserId(authentication), targetId, targetType, permission);
  }

  /**
   * Indicates whether permission evaluation is disabled. When this is true, {@code hasPermission}
   * calls should return true. This should be overridden to allow for toggling this evaluator at
   * runtime.
   */
  protected boolean isDisabled() {
    return false;
  }

  /**
   * Alternative method for evaluating a permission where only the identifier of the user and target
   * object is available, rather than the authenticated user and target objects themselves.
   *
   * @param username identifier for user to check permissions for
   * @param targetId identifier of the target resource to check permissions
   * @param targetType the type of the target resource being checked
   * @param permission the permission being validated
   * @return true if the permission is granted
   */
  public abstract boolean hasPermission(
      String username, Serializable targetId, String targetType, Object permission);
}
