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
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RestController
import retrofit.RetrofitError

@Slf4j
@RestController
@RequestMapping("/serviceAccounts")
@ConditionalOnExpression('${spinnaker.gcs.enabled:false} || ${spinnaker.s3.enabled:false} || ${spinnaker.azs.enabled:false} || ${spinnaker.oracle.enabled:false}')
public class ServiceAccountsController {

  @Autowired
  ServiceAccountDAO serviceAccountDAO;

  @Autowired(required = false)
  FiatService fiatService

  @Autowired
  FiatClientConfigurationProperties fiatClientConfigurationProperties

  @Autowired
  FiatPermissionEvaluator fiatPermissionEvaluator

  @Value('${fiat.role-sync.enabled:true}')
  Boolean roleSync

  @RequestMapping(method = RequestMethod.GET)
  Set<ServiceAccount> getAllServiceAccounts() {
    serviceAccountDAO.all();
  }

  @RequestMapping(method = RequestMethod.POST)
  ServiceAccount createServiceAccount(@RequestBody ServiceAccount serviceAccount) {
    def acct = serviceAccountDAO.create(serviceAccount.id, serviceAccount)
    syncUsers(acct)
    return acct
  }

  @RequestMapping(method = RequestMethod.DELETE, value = "/{serviceAccountId:.+}")
  void deleteServiceAccount(@PathVariable String serviceAccountId) {
    def acct = serviceAccountDAO.findById(serviceAccountId)
    serviceAccountDAO.delete(serviceAccountId)
    try {
      fiatService.logoutUser(serviceAccountId)
    } catch (RetrofitError re) {
      log.warn("Could not delete service account user $serviceAccountId", re)
    }
    syncUsers(acct)
  }

  private void syncUsers(ServiceAccount serviceAccount) {
    if (!fiatClientConfigurationProperties.enabled || !fiatService || !serviceAccount || !roleSync) {
      return
    }
    try {
      fiatService.sync(serviceAccount.memberOf)
      log.debug("Synced users with roles")
      // Invalidate the current user's permissions in the local cache
      Authentication auth = SecurityContextHolder.getContext().getAuthentication()
      fiatPermissionEvaluator.invalidatePermission((String) auth?.principal)
    } catch (RetrofitError re) {
      log.warn("Error syncing users", re)
    }
  }
}
