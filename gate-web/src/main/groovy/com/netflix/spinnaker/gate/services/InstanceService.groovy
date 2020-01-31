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
import com.netflix.spinnaker.gate.services.commands.HystrixFactory
import com.netflix.spinnaker.gate.services.internal.ClouddriverServiceSelector
import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@CompileStatic
@Component
class InstanceService {
  private static final String GROUP = "instances"

  @Autowired
  ClouddriverServiceSelector clouddriverServiceSelector

  @Autowired
  InsightConfiguration insightConfiguration

  @Autowired
  ProviderLookupService providerLookupService

  @Autowired
  ObjectMapper objectMapper

  Map getForAccountAndRegion(String account, String region, String instanceId, String selectorKey) {
    HystrixFactory.newMapCommand(GROUP, "getInstancesForAccountAndRegion-${providerLookupService.providerForAccount(account)}") {
      def service = clouddriverServiceSelector.select()
      def accountDetails = objectMapper.convertValue(service.getAccount(account), Map)
      def instanceDetails = service.getInstanceDetails(account, region, instanceId)
      def instanceContext = instanceDetails.collectEntries {
        return it.value instanceof String ? [it.key, it.value] : [it.key, ""]
      } as Map<String, String>
      String application
      if (instanceContext.serverGroup) {
        application = Names.parseName(instanceContext.serverGroup)?.app
      }
      def context = getContext(account, region, instanceId) + instanceContext + accountDetails + ["application": application]
      return instanceDetails + [
          "insightActions": insightConfiguration.instance.findResults { it.applyContext(context) }
      ]
    } execute()
  }

  Map getConsoleOutput(String account, String region, String instanceId, String provider, String selectorKey) {
    HystrixFactory.newMapCommand(GROUP, "getConsoleOutput-${providerLookupService.providerForAccount(account)}") {
      return clouddriverServiceSelector.select().getConsoleOutput(account, region, instanceId, provider)
    } execute()
  }

  static Map<String, String> getContext(String account, String region, String instanceId) {
    return ["account": account, "region": region, "instanceId": instanceId]
  }
}
