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

package com.netflix.spinnaker.clouddriver.controllers

import com.netflix.frigga.Names
import com.netflix.spinnaker.clouddriver.model.EntityTags
import com.netflix.spinnaker.clouddriver.model.LoadBalancerProvider
import com.netflix.spinnaker.clouddriver.security.AccountCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import com.netflix.spinnaker.fiat.shared.FiatPermissionEvaluator
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component

/**
 * Support for controllers requiring authorization checks from Fiat.
 */
@Component
class AuthorizationSupport {

  @Autowired
  FiatPermissionEvaluator permissionEvaluator

  @Autowired
  AccountCredentialsProvider accountCredentialsProvider

  /**
   * Performs READ authorization checks on returned Maps that are keyed by account name.
   * @param map Objected returned by a controller that has account names as the key
   * @return true always, to conform to Spring Security annotation expectation.
   */
  boolean filterForAccounts(Map<String, Object> map) {
    if (!map) {
      return true
    }

    Authentication auth = SecurityContextHolder.context.authentication;

    new HashMap(map).keySet().each { String account ->
      if (!permissionEvaluator.hasPermission(auth, account, 'ACCOUNT', 'READ')) {
        map.remove(account)
      }
    }
    return true
  }

  /**
   *   Used for filtering result lists by searching for "account"-like properties.
   */
  boolean filterForAccounts(List items) {
    if (!items) {
      return true
    }

    Authentication auth = SecurityContextHolder.context.authentication;

    new ArrayList<>(items).each { Object item ->
      Map propertySource = item.properties
      if (item instanceof Map) {
        propertySource = item
      }
      String account = propertySource.account ?: propertySource.accountName

      if (account && !permissionEvaluator.hasPermission(auth, account, 'ACCOUNT', 'READ')) {
        items.remove(item)
      }
    }
    return true
  }

  boolean filterLoadBalancerProviderItems(List<LoadBalancerProvider.Item> lbItems) {
    if (!lbItems) {
      return true
    }

    new ArrayList<>(lbItems).each { LoadBalancerProvider.Item lbItem ->
      if(!filterLoadBalancerProviderItem(lbItem)) {
        lbItems.remove(lbItem)
      }
    }
    return true
  }

  boolean filterLoadBalancerProviderItem(LoadBalancerProvider.Item lbItem) {
    if (!lbItem) {
      return false
    }

    String application = Names.parseName(lbItem.name).app
    if (!application) {
      return false
    }

    Authentication auth = SecurityContextHolder.context.authentication;

    if (!permissionEvaluator.hasPermission(auth, application, 'APPLICATION', 'READ')) {
      return false
    }

    new ArrayList<>(lbItem.byAccounts).each { LoadBalancerProvider.ByAccount account ->
      if (!permissionEvaluator.hasPermission(auth, account.name, 'ACCOUNT', 'READ')) {
        lbItem.byAccounts.remove(account)
      }
    }

    // It'd be weird if there was a load balancer with just name and an empty accounts field.
    if (!lbItem.byAccounts) {
      return false
    }
    return true
  }

  /**
   * Verify that the current user has access to the application/account (as appropriate) for each tagged entity.
   */
  boolean authorizeEntityTags(List<EntityTags> entityTags) {
    if (!entityTags) {
      return false
    }

    Map<String, String> accountNameById = accountCredentialsProvider.all.collectEntries { AccountCredentials credentials ->
      [credentials.accountId?.toString(), credentials.name]
    }

    def auth = SecurityContextHolder.context.authentication;
    return entityTags.every {
      boolean hasPermission = true

      if (it.entityRef.application) {
        hasPermission = hasPermission && permissionEvaluator.hasPermission(auth, it.entityRef.application, 'APPLICATION', 'READ')
      }

      String accountName = accountNameById[it.entityRef.accountId]
      if (accountName) {
        hasPermission = hasPermission && permissionEvaluator.hasPermission(auth, accountName, 'ACCOUNT', 'READ')
      }

      return hasPermission
    }
  }
}
