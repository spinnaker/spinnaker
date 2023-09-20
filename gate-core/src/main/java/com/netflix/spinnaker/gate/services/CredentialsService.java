/*
 * Copyright 2014 Netflix, Inc.
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
 */

package com.netflix.spinnaker.gate.services;

import com.netflix.spinnaker.fiat.model.Authorization;
import com.netflix.spinnaker.fiat.shared.FiatStatus;
import com.netflix.spinnaker.gate.services.internal.ClouddriverService;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

@Log4j2
@Service
@RequiredArgsConstructor
public class CredentialsService {
  private final AccountLookupService accountLookupService;
  private final FiatStatus fiatStatus;

  public Collection<String> getAccountNames(@Nullable Collection<String> userRoles) {
    return getAccounts(userRoles, false).stream()
        .map(ClouddriverService.Account::getName)
        .collect(Collectors.toList());
  }

  public Collection<String> getAccountNames(
      @Nullable Collection<String> userRoles, boolean ignoreFiatStatus) {
    return getAccounts(userRoles, ignoreFiatStatus).stream()
        .map(ClouddriverService.Account::getName)
        .collect(Collectors.toList());
  }

  /** Returns all account names that a user with the specified list of userRoles has access to. */
  List<ClouddriverService.AccountDetails> getAccounts(
      @Nullable Collection<String> userRoles, boolean ignoreFiatStatus) {
    Set<String> userRolesLower =
        userRoles == null
            ? Set.of()
            : userRoles.stream()
                .filter(Objects::nonNull)
                .map(role -> role.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
    return accountLookupService.getAccounts().stream()
        .filter(
            account -> {
              if (!ignoreFiatStatus && fiatStatus.isEnabled()) {
                return true; // Returned list is filtered later.
              }

              Map<String, Collection<String>> permissions = account.getPermissions();
              if (CollectionUtils.isEmpty(permissions)) {
                return true;
              }
              Set<String> permittedRoles =
                  permissions.getOrDefault(Authorization.WRITE.name(), Set.of()).stream()
                      .map(role -> role.toLowerCase(Locale.ROOT))
                      .collect(Collectors.toSet());
              return !Collections.disjoint(userRolesLower, permittedRoles);
            })
        .collect(Collectors.toList());
  }
}
