/*
 * Copyright 2014 Netflix, Inc.
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


package com.netflix.spinnaker.gate.services

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.frigga.Names
import com.netflix.spinnaker.gate.config.InsightConfiguration
import com.netflix.spinnaker.gate.services.internal.ClouddriverServiceSelector
import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import retrofit.RetrofitError

@CompileStatic
@Component
class ServerGroupService {

  @Autowired
  ClouddriverServiceSelector clouddriverServiceSelector

  @Autowired
  InsightConfiguration insightConfiguration

  @Autowired
  ObjectMapper objectMapper

  @Autowired
  ProviderLookupService providerLookupService

  List getForApplication(String applicationName, String expand, String cloudProvider, String clusters, String selectorKey) {
    String commandKey = Boolean.valueOf(expand) ? "getExpandedServerGroupsForApplication" : "getServerGroupsForApplication"
    clouddriverServiceSelector.select().getServerGroups(applicationName, expand, cloudProvider, clusters)
  }

  List getForApplications(List<String> applications, String cloudProvider, String selectorKey) {
    clouddriverServiceSelector.select().getServerGroups(applications, null, cloudProvider)
  }

  List getForIds(List<String> ids, String cloudProvider, String selectorKey) {
    clouddriverServiceSelector.select().getServerGroups(null, ids, cloudProvider)
  }

  Map getForApplicationAndAccountAndRegion(String applicationName, String account, String region, String serverGroupName, String selectorKey, String includeDetails) {
    try {
      def service = clouddriverServiceSelector.select()
      def accountDetails = objectMapper.convertValue(service.getAccount(account), Map)
      def serverGroupDetails = service.getServerGroupDetails(applicationName, account, region, serverGroupName, includeDetails)
      def serverGroupContext = serverGroupDetails.collectEntries {
        return it.value instanceof String ? [it.key, it.value] : [it.key, ""]
      } as Map<String, String>

      def context = getContext(applicationName, account, region, serverGroupName) + serverGroupContext + accountDetails
      return serverGroupDetails + [
        "insightActions": insightConfiguration.serverGroup.findResults { it.applyContext(context) }
      ]
    } catch (RetrofitError e) {
      if (e.response?.status == 404) {
        return [:]
      }
      throw e
    }
  }

  static Map<String, String> getContext(String application, String account, String region, String serverGroup) {
    String cluster = Names.parseName(serverGroup).cluster
    return [
      "application": application,
      "account": account,
      "region": region,
      "serverGroup": serverGroup,
      "cluster": cluster
    ]
  }
}
