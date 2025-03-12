/*
 * Copyright 2020 OpsMx, Inc.
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
 */

package com.netflix.spinnaker.orca.echo.util;

import com.google.common.base.Strings;
import com.google.common.collect.Sets;
import com.netflix.spinnaker.fiat.model.UserPermission;
import com.netflix.spinnaker.fiat.model.resources.Role;
import com.netflix.spinnaker.fiat.shared.FiatPermissionEvaluator;
import com.netflix.spinnaker.fiat.shared.FiatStatus;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ManualJudgmentAuthorization {
  private final Logger log = LoggerFactory.getLogger(getClass());

  private final FiatPermissionEvaluator fiatPermissionEvaluator;

  private final FiatStatus fiatStatus;

  @Autowired
  public ManualJudgmentAuthorization(
      Optional<FiatPermissionEvaluator> fiatPermissionEvaluator, FiatStatus fiatStatus) {
    this.fiatPermissionEvaluator = fiatPermissionEvaluator.orElse(null);

    this.fiatStatus = fiatStatus;
  }

  /**
   * A manual judgment will be considered "authorized" if the current user has at least one of the
   * required judgment roles (or the current user is an admin).
   *
   * @param requiredJudgmentRoles Required judgment roles
   * @param currentUser User that has attempted this judgment
   * @return whether or not {@param currentUser} has authorization to judge
   */
  public boolean isAuthorized(Collection<String> requiredJudgmentRoles, String currentUser) {
    if (!fiatStatus.isEnabled() || requiredJudgmentRoles.isEmpty()) {
      return true;
    }

    if (Strings.isNullOrEmpty(currentUser)) {
      return false;
    }

    UserPermission.View permission = fiatPermissionEvaluator.getPermission(currentUser);
    if (permission == null) { // Should never happen?
      log.warn("Attempted to get user permission for '{}' but none were found.", currentUser);
      return false;
    }

    return permission.isAdmin()
        || isAuthorized(
            requiredJudgmentRoles,
            permission.getRoles().stream().map(Role.View::getName).collect(Collectors.toList()));
  }

  private boolean isAuthorized(
      Collection<String> requiredJudgmentRoles, Collection<String> currentUserRoles) {
    if (requiredJudgmentRoles == null || requiredJudgmentRoles.isEmpty()) {
      return true;
    }

    if (currentUserRoles == null) {
      currentUserRoles = new ArrayList<>();
    }

    return !Sets.intersection(new HashSet<>(requiredJudgmentRoles), new HashSet<>(currentUserRoles))
        .isEmpty();
  }
}
