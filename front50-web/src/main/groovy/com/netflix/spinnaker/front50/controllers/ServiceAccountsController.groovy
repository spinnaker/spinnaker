/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.front50.controllers

import com.netflix.spinnaker.fiat.shared.FiatClientConfigurationProperties
import com.netflix.spinnaker.fiat.shared.FiatPermissionEvaluator
import com.netflix.spinnaker.fiat.shared.FiatService
import com.netflix.spinnaker.front50.model.serviceaccount.ServiceAccount
import com.netflix.spinnaker.front50.model.serviceaccount.ServiceAccountDAO
import com.netflix.spinnaker.kork.exceptions.SystemException
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.*
import retrofit.RetrofitError

@Slf4j
@RestController
@RequestMapping("/serviceAccounts")
public class ServiceAccountsController {

  Optional<ServiceAccountDAO> serviceAccountDAO
  Optional<FiatService> fiatService
  FiatClientConfigurationProperties fiatClientConfigurationProperties
  FiatPermissionEvaluator fiatPermissionEvaluator
  Boolean roleSync

  ServiceAccountsController(
    Optional<ServiceAccountDAO> serviceAccountDAO,
    Optional<FiatService> fiatService,
    FiatClientConfigurationProperties fiatClientConfigurationProperties,
    FiatPermissionEvaluator fiatPermissionEvaluator,
    @Value('${fiat.role-sync.enabled:true}') Boolean roleSync
  ) {
    this.serviceAccountDAO = serviceAccountDAO
    this.fiatService = fiatService
    this.fiatClientConfigurationProperties = fiatClientConfigurationProperties
    this.fiatPermissionEvaluator = fiatPermissionEvaluator
    this.roleSync = roleSync
  }

  @RequestMapping(method = RequestMethod.GET)
  Set<ServiceAccount> getAllServiceAccounts() {
    serviceAccountDAO().all()
  }

  @RequestMapping(method = RequestMethod.POST)
  ServiceAccount createServiceAccount(@RequestBody ServiceAccount serviceAccount) {
    def acct = serviceAccountDAO().create(serviceAccount.id, serviceAccount)
    syncUsers(acct)
    return acct
  }

  @RequestMapping(method = RequestMethod.DELETE, value = "/{serviceAccountId:.+}")
  void deleteServiceAccount(@PathVariable String serviceAccountId) {
    def acct = serviceAccountDAO().findById(serviceAccountId)
    serviceAccountDAO().delete(serviceAccountId)
    try {
      if (fiatService.isPresent()) {
        fiatService.get().logoutUser(serviceAccountId)
      }
    } catch (RetrofitError re) {
      log.warn("Could not delete service account user $serviceAccountId", re)
    }
    syncUsers(acct)
  }

  private void syncUsers(ServiceAccount serviceAccount) {
    if (!fiatClientConfigurationProperties.enabled || !fiatService.isPresent() || !serviceAccount || !roleSync) {
      return
    }
    try {
      fiatService.get().sync(serviceAccount.memberOf)
      log.debug("Synced users with roles")
      // Invalidate the current user's permissions in the local cache
      Authentication auth = SecurityContextHolder.getContext().getAuthentication()
      fiatPermissionEvaluator.invalidatePermission((String) auth?.principal)
    } catch (RetrofitError re) {
      log.warn("Error syncing users", re)
    }
  }

   private ServiceAccountDAO serviceAccountDAO() {
    if (!serviceAccountDAO.isPresent()) {
      throw new SystemException("Configured storage service does not support service account permissions")
    }

    return serviceAccountDAO.get()
  }

}
