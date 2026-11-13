/*
 * Copyright 2026 Harness, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.security;

import com.netflix.spinnaker.fiat.model.Authorization;
import com.netflix.spinnaker.fiat.model.UserPermission;
import com.netflix.spinnaker.fiat.model.resources.Account;
import com.netflix.spinnaker.fiat.shared.FiatPermissionEvaluator;
import com.netflix.spinnaker.fiat.shared.FiatStatus;
import com.netflix.spinnaker.orca.config.ExecutionConfigurationProperties;
import com.netflix.spinnaker.security.AuthenticatedRequest;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Resolves the accounts a user can write to from Fiat, for recording on an execution's {@code
 * authentication.allowedAccounts}. The result is never taken from request headers.
 *
 * <p>Resolves to nothing when Fiat is disabled or unavailable, or when {@code
 * executions.includeAllowedAccounts} is false.
 */
@Component
public class AllowedAccountsResolver {
  private final Optional<FiatStatus> fiatStatus;
  private final Optional<FiatPermissionEvaluator> fiatPermissionEvaluator;
  private final ExecutionConfigurationProperties executionConfigurationProperties;

  public AllowedAccountsResolver(
      Optional<FiatStatus> fiatStatus,
      Optional<FiatPermissionEvaluator> fiatPermissionEvaluator,
      ExecutionConfigurationProperties executionConfigurationProperties) {
    this.fiatStatus = fiatStatus;
    this.fiatPermissionEvaluator = fiatPermissionEvaluator;
    this.executionConfigurationProperties = executionConfigurationProperties;
  }

  public Collection<String> resolve(String user) {
    if (user == null
        || user.isEmpty()
        || !executionConfigurationProperties.isIncludeAllowedAccounts()
        || fiatStatus.filter(FiatStatus::isEnabled).isEmpty()
        || fiatPermissionEvaluator.isEmpty()) {
      return Set.of();
    }

    UserPermission.View permission =
        AuthenticatedRequest.allowAnonymous(
            () -> fiatPermissionEvaluator.get().getPermission(user));
    if (permission == null) {
      return Set.of();
    }

    return permission.getAccounts().stream()
        .filter(account -> account.getAuthorizations().contains(Authorization.WRITE))
        .map(Account.View::getName)
        .collect(Collectors.toSet());
  }
}
