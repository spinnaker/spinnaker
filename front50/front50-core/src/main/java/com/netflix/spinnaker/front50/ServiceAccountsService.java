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

import com.netflix.spinnaker.front50.config.annotations.ConditionalOnAnyProviderExceptRedisIsEnabled;
import com.netflix.spinnaker.front50.model.serviceaccount.ServiceAccount;
import com.netflix.spinnaker.front50.model.serviceaccount.ServiceAccountDAO;
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException;
import java.util.Collection;
import java.util.Collections;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Persists managed service accounts in Front50's own store. In the owner-local authorization model
 * Front50 is the authoritative owner of service-account ACLs, so there is no external permission
 * store to sync to: the run-as minter resolves a service account's roles directly from this store
 * at token-mint time (see the run-as token endpoint).
 */
@Service
@ConditionalOnAnyProviderExceptRedisIsEnabled
public class ServiceAccountsService {
  private static final Logger log = LoggerFactory.getLogger(ServiceAccountsService.class);
  private static final String MANAGED_SERVICE_ACCOUNT_SUFFIX = "@managed-service-account";

  private final ServiceAccountDAO serviceAccountDAO;

  public ServiceAccountsService(ServiceAccountDAO serviceAccountDAO) {
    this.serviceAccountDAO = serviceAccountDAO;
  }

  public Collection<ServiceAccount> getAllServiceAccounts() {
    return serviceAccountDAO.all();
  }

  public ServiceAccount createServiceAccount(ServiceAccount serviceAccount) {
    return serviceAccountDAO.create(serviceAccount.getId(), serviceAccount);
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
          } catch (Exception e) {
            log.warn("Could not delete service account user {}", sa.getId(), e);
          }
        });
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
}
