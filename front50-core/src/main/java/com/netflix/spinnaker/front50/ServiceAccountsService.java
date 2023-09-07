/*
 * Copyright 2020 Adevinta
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

package com.netflix.spinnaker.front50;

import com.netflix.spinnaker.fiat.shared.FiatClientConfigurationProperties;
import com.netflix.spinnaker.fiat.shared.FiatPermissionEvaluator;
import com.netflix.spinnaker.fiat.shared.FiatService;
import com.netflix.spinnaker.front50.config.FiatConfigurationProperties;
import com.netflix.spinnaker.front50.config.annotations.ConditionalOnAnyProviderExceptRedisIsEnabled;
import com.netflix.spinnaker.front50.model.serviceaccount.ServiceAccount;
import com.netflix.spinnaker.front50.model.serviceaccount.ServiceAccountDAO;
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerServerException;
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnAnyProviderExceptRedisIsEnabled
public class ServiceAccountsService {
  private static final Logger log = LoggerFactory.getLogger(ServiceAccountsService.class);
  private static final String MANAGED_SERVICE_ACCOUNT_SUFFIX = "@managed-service-account";

  private final ServiceAccountDAO serviceAccountDAO;
  private final Optional<FiatService> fiatService;
  private final FiatClientConfigurationProperties fiatClientConfigurationProperties;
  private final FiatConfigurationProperties fiatConfigurationProperties;
  private final FiatPermissionEvaluator fiatPermissionEvaluator;

  public ServiceAccountsService(
      ServiceAccountDAO serviceAccountDAO,
      Optional<FiatService> fiatService,
      FiatClientConfigurationProperties fiatClientConfigurationProperties,
      FiatConfigurationProperties fiatConfigurationProperties,
      FiatPermissionEvaluator fiatPermissionEvaluator) {
    this.serviceAccountDAO = serviceAccountDAO;
    this.fiatService = fiatService;
    this.fiatClientConfigurationProperties = fiatClientConfigurationProperties;
    this.fiatConfigurationProperties = fiatConfigurationProperties;
    this.fiatPermissionEvaluator = fiatPermissionEvaluator;
  }

  public Collection<ServiceAccount> getAllServiceAccounts() {
    return serviceAccountDAO.all();
  }

  public ServiceAccount createServiceAccount(ServiceAccount serviceAccount) {
    ServiceAccount acct = serviceAccountDAO.create(serviceAccount.getId(), serviceAccount);
    if (fiatConfigurationProperties.isDisableRoleSyncWhenSavingServiceAccounts()) {
      syncServiceAccount(acct);
    } else {
      syncUsers(Collections.singletonList(acct));
    }
    return acct;
  }

  public void deleteServiceAccount(String serviceAccountId) {
    ServiceAccount acct = serviceAccountDAO.findById(serviceAccountId);
    deleteServiceAccounts(Collections.singletonList(acct));
  }

  public void deleteServiceAccounts(Collection<ServiceAccount> serviceAccountsToDelete) {
    serviceAccountsToDelete.forEach(
        sa -> {
          try {
            serviceAccountDAO.delete(sa.getId());
            fiatService.ifPresent(service -> service.logoutUser(sa.getId()));
          } catch (Exception e) {
            log.warn("Could not delete service account user {}", sa.getId(), e);
          }
        });

    if (!serviceAccountsToDelete.isEmpty()) {
      syncUsers(serviceAccountsToDelete);
    }
  }

  public void deleteManagedServiceAccounts(Collection<String> prefixes) {
    Collection<ServiceAccount> serviceAccountsToDelete =
        prefixes.stream()
            .map(p -> p + MANAGED_SERVICE_ACCOUNT_SUFFIX)
            .flatMap(
                sa -> {
                  try {
                    ServiceAccount managedServiceAccount = serviceAccountDAO.findById(sa);
                    return Stream.of(managedServiceAccount);
                  } catch (NotFoundException e) {
                    return Stream.empty();
                  }
                })
            .collect(Collectors.toList());

    deleteServiceAccounts(serviceAccountsToDelete);
  }

  private void syncUsers(Collection<ServiceAccount> serviceAccounts) {
    if (!fiatClientConfigurationProperties.isEnabled()
        || !fiatService.isPresent()
        || serviceAccounts == null
        || !fiatConfigurationProperties.getRoleSync().isEnabled()) {
      return;
    }
    try {
      List<String> rolesToSync =
          serviceAccounts.stream()
              .map(ServiceAccount::getMemberOf)
              .flatMap(Collection::stream)
              .distinct()
              .collect(Collectors.toList());
      fiatService.get().sync(rolesToSync);
      log.debug("Synced users with roles: {}", rolesToSync);
      // Invalidate the current user's permissions in the local cache
      Authentication auth = SecurityContextHolder.getContext().getAuthentication();
      if (auth != null) {
        fiatPermissionEvaluator.invalidatePermission((String) auth.getPrincipal());
      }
    } catch (SpinnakerServerException re) {
      log.warn("Error syncing users", re);
    }
  }

  private void syncServiceAccount(ServiceAccount serviceAccount) {
    if (!fiatClientConfigurationProperties.isEnabled()
        || fiatService.isEmpty()
        || serviceAccount == null
        || !fiatConfigurationProperties.getRoleSync().isEnabled()) {
      return;
    }
    try {
      fiatService.get().syncServiceAccount(serviceAccount.getId(), serviceAccount.getMemberOf());
      log.debug(
          "Synced service account {} with roles: {}",
          serviceAccount.getId(),
          serviceAccount.getMemberOf());
      // Invalidate the current user's permissions in the local cache
      Authentication auth = SecurityContextHolder.getContext().getAuthentication();
      if (auth != null) {
        fiatPermissionEvaluator.invalidatePermission((String) auth.getPrincipal());
      }
    } catch (SpinnakerServerException re) {
      log.warn("Error syncing service account with service account only endpoint", re);
    }
  }
}
