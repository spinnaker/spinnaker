/*
 * Copyright 2016 Netflix, Inc.
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
import com.netflix.spinnaker.gate.services.internal.ClouddriverService;
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall;
import com.netflix.spinnaker.security.AuthenticatedRequest;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

@Log4j2
@Service("providerLookupService")
@RequiredArgsConstructor
public class DefaultProviderLookupService implements ProviderLookupService, AccountLookupService {
  private static final String FALLBACK = "unknown";
  private final ClouddriverService clouddriverService;

  private volatile List<ClouddriverService.AccountDetails> accountsCache = List.of();

  @Scheduled(fixedDelay = 30, timeUnit = TimeUnit.SECONDS)
  void refreshCache() {
    try {
      accountsCache = loadAccounts();
    } catch (Exception e) {
      log.error("Unable to refresh account details cache", e);
    }
  }

  private List<ClouddriverService.AccountDetails> loadAccounts() {
    var accounts =
        AuthenticatedRequest.allowAnonymous(
            () -> Retrofit2SyncCall.execute(clouddriverService.getAccountDetails()));
    // migration support, prefer permissions configuration, translate requiredGroupMembership
    // (for CredentialsService in non fiat mode) into permissions collection.
    //
    // Ignore explicitly set requiredGroupMemberships if permissions are also present.
    for (var account : accounts) {
      Map<String, Collection<String>> permissions = account.getPermissions();
      Collection<String> requiredGroupMembership = account.getRequiredGroupMembership();
      if (permissions != null) {
        for (var entry : permissions.entrySet()) {
          entry.setValue(toLowerCase(entry.getValue()).collect(Collectors.toList()));
        }

        if (!CollectionUtils.isEmpty(requiredGroupMembership)) {
          Set<String> rgmSet = toLowerCase(requiredGroupMembership).collect(Collectors.toSet());
          Collection<String> permittedRoles = permissions.get(Authorization.WRITE.name());
          if (!rgmSet.equals(permittedRoles)) {
            log.warn(
                "On account {}: preferring permissions: {} over requiredGroupMemberships: {} for authorization decision",
                account.getName(),
                permissions,
                rgmSet);
          }
        }
      } else {
        if (CollectionUtils.isEmpty(requiredGroupMembership)) {
          account.setPermissions(Map.of());
        } else {
          List<String> rgm = toLowerCase(requiredGroupMembership).collect(Collectors.toList());
          account.setRequiredGroupMembership(rgm);
          account.setPermissions(
              Map.of(
                  Authorization.READ.name(), rgm,
                  Authorization.WRITE.name(), rgm));
        }
      }
    }
    return accounts;
  }

  @Override
  public String providerForAccount(String account) {
    return accountsCache.stream()
        .filter(it -> account.equals(it.getName()))
        .map(ClouddriverService.Account::getType)
        .findFirst()
        .orElse(FALLBACK);
  }

  @Override
  public List<ClouddriverService.AccountDetails> getAccounts() {
    return accountsCache;
  }

  private static Stream<String> toLowerCase(Collection<String> strings) {
    return strings.stream().map(s -> s.toLowerCase(Locale.ROOT));
  }
}
