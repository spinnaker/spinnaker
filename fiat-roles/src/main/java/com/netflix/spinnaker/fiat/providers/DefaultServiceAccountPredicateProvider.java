/*
 * Copyright 2021 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.fiat.providers;

import com.netflix.spinnaker.fiat.config.FiatRoleConfig;
import com.netflix.spinnaker.fiat.model.resources.ServiceAccount;
import java.util.List;
import java.util.function.Predicate;

/**
 * Allows access to a service account if the authenticated is an administrator or has overlapping
 * membership in the roles required by the service account.
 */
public class DefaultServiceAccountPredicateProvider implements ServiceAccountPredicateProvider {
  private final FiatRoleConfig fiatRoleConfig;

  public DefaultServiceAccountPredicateProvider(FiatRoleConfig fiatRoleConfig) {
    this.fiatRoleConfig = fiatRoleConfig;
  }

  @Override
  public Predicate<ServiceAccount> get(String userId, List<String> userRoles, boolean isAdmin) {
    if (isAdmin) {
      return svcAcct -> true;
    }

    if (fiatRoleConfig.isOrMode()) {
      return svcAcct -> svcAcct.getMemberOf().stream().anyMatch(userRoles::contains);
    } else {
      return svcAcct -> userRoles.containsAll(svcAcct.getMemberOf());
    }
  }
}
