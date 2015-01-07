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

import com.netflix.spinnaker.gate.config.InsightConfiguration
import com.netflix.spinnaker.gate.services.commands.HystrixFactory
import com.netflix.spinnaker.gate.services.internal.OortService
import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@CompileStatic
@Component
class ServerGroupService {
  private static final String GROUP = "serverGroups"

  @Autowired
  OortService oortService

  @Autowired
  InsightConfiguration insightConfiguration

  List getForApplication(String applicationName) {
    HystrixFactory.newListCommand(GROUP, "serverGroups-${applicationName}", true) {
      oortService.getServerGroups(applicationName)
    } execute()
  }

  Map getForApplicationAndAccountAndRegion(String applicationName, String account, String region, String serverGroupName) {
    HystrixFactory.newMapCommand(GROUP, "serverGroups-${applicationName}-${account}-${region}-${serverGroupName}", true) {
      def context = getContext(applicationName, account, region, serverGroupName)
      return oortService.getServerGroupDetails(applicationName, account, region, serverGroupName) + [
          "insightActions": insightConfiguration.serverGroup.collect { it.applyContext(context) }
      ]
    } execute()
  }

  static Map<String, String> getContext(String application, String account, String region, String serverGroup) {
    return ["application": application, "account": account, "region": region, "serverGroup": serverGroup]
  }
}