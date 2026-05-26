/*
 * Copyright 2016 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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
package com.netflix.spinnaker.front50.controllers;

import com.netflix.spinnaker.front50.ServiceAccountsService;
import com.netflix.spinnaker.front50.config.ServiceAccountsProperties;
import com.netflix.spinnaker.front50.config.annotations.ConditionalOnAnyProviderExceptRedisIsEnabled;
import com.netflix.spinnaker.front50.model.serviceaccount.ServiceAccount;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/serviceAccounts")
@ConditionalOnAnyProviderExceptRedisIsEnabled
public class ServiceAccountsController {

  private final ServiceAccountsService serviceAccountService;
  private final ServiceAccountsProperties apiServiceAccountsProperties;

  public ServiceAccountsController(
      ServiceAccountsService serviceAccountService,
      ServiceAccountsProperties apiServiceAccountsProperties) {
    this.serviceAccountService = serviceAccountService;
    this.apiServiceAccountsProperties = apiServiceAccountsProperties;
  }

  @RequestMapping(method = RequestMethod.GET)
  public Set<ServiceAccount> getAllServiceAccounts() {
    return new HashSet<>(serviceAccountService.getAllServiceAccounts());
  }

  /**
   * Returns the subset of service accounts that are declared under {@code service-accounts} in
   * configuration and are therefore eligible for API token minting.
   */
  @RequestMapping(method = RequestMethod.GET, value = "/tokenEligible")
  public Set<ServiceAccount> getTokenEligibleServiceAccounts() {
    Set<String> configuredNames =
        apiServiceAccountsProperties.getServiceAccounts().stream()
            .map(ServiceAccountsProperties.ServiceAccountDefinition::getName)
            .collect(Collectors.toSet());

    return serviceAccountService.getAllServiceAccounts().stream()
        .filter(sa -> configuredNames.contains(sa.getName()))
        .collect(Collectors.toSet());
  }

  @RequestMapping(method = RequestMethod.POST)
  public ServiceAccount createServiceAccount(@RequestBody ServiceAccount serviceAccount) {
    return serviceAccountService.createServiceAccount(serviceAccount);
  }

  @RequestMapping(method = RequestMethod.DELETE, value = "/{serviceAccountId:.+}")
  public void deleteServiceAccount(@PathVariable String serviceAccountId) {
    serviceAccountService.deleteServiceAccount(serviceAccountId);
  }
}
