/*
 * Copyright 2015 Google, Inc.
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

package com.netflix.spinnaker.rosco.controllers

import com.netflix.spinnaker.rosco.api.Bake
import com.netflix.spinnaker.rosco.api.BakeRequest
import com.netflix.spinnaker.rosco.api.BakeStatus
import com.netflix.spinnaker.rosco.config.RoscoConfiguration
import com.netflix.spinnaker.rosco.providers.CloudProviderBakeHandler
import com.netflix.spinnaker.rosco.providers.registry.CloudProviderBakeHandlerRegistry
import com.netflix.spinnaker.rosco.rush.api.RushService
import com.netflix.spinnaker.rosco.rush.api.ScriptExecution
import com.netflix.spinnaker.rosco.rush.api.ScriptRequest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1")
class BakeryController {

  @Autowired
  ScriptRequest baseScriptRequest

  @Autowired
  RushService rushService

  @Autowired
  CloudProviderBakeHandlerRegistry cloudProviderBakeHandlerRegistry

  @Autowired
  RoscoConfiguration.ExecutionStatusToBakeStateMap executionStatusToBakeStateMap

  @RequestMapping(value = '/{region}/bake', method = RequestMethod.POST)
  BakeStatus createBake(@PathVariable("region") String region, @RequestBody BakeRequest bakeRequest) {
    CloudProviderBakeHandler cloudProviderBakeHandler = cloudProviderBakeHandlerRegistry.lookup(bakeRequest.cloud_provider_type)

    if (cloudProviderBakeHandler) {
      def packerCommand = cloudProviderBakeHandler.producePackerCommand(region, bakeRequest)
      def scriptRequest = baseScriptRequest.copyWith(command: packerCommand)
      def scriptId = rushService.runScript(scriptRequest).toBlocking().single()

      return new BakeStatus(id: scriptId.id, resource_id: scriptId.id, state: BakeStatus.State.PENDING)
    } else {
      throw new IllegalArgumentException("Unknown provider type '$bakeRequest.cloud_provider_type'.")
    }
  }

  @RequestMapping(value = "/{region}/status/{statusId}", method = RequestMethod.GET)
  BakeStatus lookupStatus(@PathVariable("region") String region, @PathVariable("statusId") String statusId) {
    ScriptExecution scriptExecution = rushService.scriptDetails(statusId).toBlocking().single()

    return new BakeStatus(id: scriptExecution.id,
                          resource_id: scriptExecution.id,
                          state: executionStatusToBakeStateMap.convertExecutionStatusToBakeState(scriptExecution.status))
  }

  @RequestMapping(value = "/{region}/bake/{bakeId}", method = RequestMethod.GET)
  Bake lookupBake(@PathVariable("region") String region, @PathVariable("bakeId") String bakeId) {
    Map logsContentMap = rushService.getLogs(bakeId, baseScriptRequest).toBlocking().single()
    def logsContentFirstLine = logsContentMap?.logsContent?.substring(0, logsContentMap?.logsContent?.indexOf("\n"))
    def cloudProviderBakeHandler = cloudProviderBakeHandlerRegistry.findProducer(logsContentFirstLine)

    return cloudProviderBakeHandler?.scrapeCompletedBakeResults(region, bakeId, logsContentMap?.logsContent)
  }

}
