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
import com.netflix.spinnaker.front50.model.serviceaccount.ServiceAccount;
import com.netflix.spinnaker.kork.exceptions.SystemException;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/serviceAccounts")
public class ServiceAccountsController {

  private final Optional<ServiceAccountsService> serviceAccountService;

  public ServiceAccountsController(Optional<ServiceAccountsService> serviceAccountService) {
    this.serviceAccountService = serviceAccountService;
  }

  @RequestMapping(method = RequestMethod.GET)
  public Set<ServiceAccount> getAllServiceAccounts() {
    return new HashSet<>(serviceAccountService().getAllServiceAccounts());
  }

  @RequestMapping(method = RequestMethod.POST)
  public ServiceAccount createServiceAccount(@RequestBody ServiceAccount serviceAccount) {
    return serviceAccountService().createServiceAccount(serviceAccount);
  }

  @RequestMapping(method = RequestMethod.DELETE, value = "/{serviceAccountId:.+}")
  public void deleteServiceAccount(@PathVariable String serviceAccountId) {
    serviceAccountService().deleteServiceAccount(serviceAccountId);
  }

  private ServiceAccountsService serviceAccountService() {
    if (!serviceAccountService.isPresent()) {
      throw new SystemException(
          "Configured storage service does not support service account permissions");
    }
    return serviceAccountService.get();
  }
}
