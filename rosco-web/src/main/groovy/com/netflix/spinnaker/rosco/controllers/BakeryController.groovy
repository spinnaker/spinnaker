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
import com.netflix.spinnaker.rosco.persistence.BakeStore
import com.netflix.spinnaker.rosco.providers.CloudProviderBakeHandler
import com.netflix.spinnaker.rosco.providers.registry.CloudProviderBakeHandlerRegistry
import com.netflix.spinnaker.rosco.rush.api.RushService
import com.netflix.spinnaker.rosco.rush.api.ScriptRequest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1")
class BakeryController {

  @Autowired
  BakeStore bakeStore

  @Autowired
  ScriptRequest baseScriptRequest

  @Autowired
  RushService rushService

  @Autowired
  CloudProviderBakeHandlerRegistry cloudProviderBakeHandlerRegistry

  @Value('${defaultCloudProviderType:aws}')
  BakeRequest.CloudProviderType defaultCloudProviderType

  @RequestMapping(value = '/{region}/bake', method = RequestMethod.POST)
  BakeStatus createBake(@PathVariable("region") String region, @RequestBody BakeRequest bakeRequest) {
    if (!bakeRequest.cloud_provider_type) {
      bakeRequest = bakeRequest.copyWith(cloud_provider_type: defaultCloudProviderType)
    }

    CloudProviderBakeHandler cloudProviderBakeHandler = cloudProviderBakeHandlerRegistry.lookup(bakeRequest.cloud_provider_type)

    if (cloudProviderBakeHandler) {
      def bakeKey = cloudProviderBakeHandler.produceBakeKey(region, bakeRequest)

      def existingBakeStatus = queryExistingBakes(bakeKey)

      if (existingBakeStatus) {
        return existingBakeStatus
      }

      def packerCommand = cloudProviderBakeHandler.producePackerCommand(region, bakeRequest)
      def scriptRequest = baseScriptRequest.copyWith(command: packerCommand)

      if (bakeStore.acquireBakeLock(bakeKey)) {
        return runBake(bakeKey, region, bakeRequest, scriptRequest)
      } else {
        def startTime = System.currentTimeMillis()

        // Poll for bake status by bake key every 1/2 second for 5 seconds.
        while (System.currentTimeMillis() - startTime < 5000) {
          def bakeStatus = bakeStore.retrieveBakeStatusByKey(bakeKey)

          if (bakeStatus) {
            return bakeStatus
          } else {
            Thread.sleep(500)
          }
        }

        // Maybe the TTL expired but the bake status wasn't set for some other reason? Let's try again before giving up.
        if (bakeStore.acquireBakeLock(bakeKey)) {
          return runBake(bakeKey, region, bakeRequest, scriptRequest)
        }

        throw new IllegalArgumentException("Unable to acquire lock and unable to determine id of lock holder for bake " +
                                           "key '$bakeKey'.")
      }
    } else {
      throw new IllegalArgumentException("Unknown provider type '$bakeRequest.cloud_provider_type'.")
    }
  }

  private BakeStatus runBake(String bakeKey, String region, BakeRequest bakeRequest, ScriptRequest scriptRequest) {
    def scriptId = rushService.runScript(scriptRequest).toBlocking().single()
    def bakeStatus = new BakeStatus(id: scriptId.id, resource_id: scriptId.id, state: BakeStatus.State.PENDING)

    bakeStore.storeBakeStatus(bakeKey, region, bakeRequest, bakeStatus)

    return bakeStatus
  }

  @RequestMapping(value = "/{region}/status/{statusId}", method = RequestMethod.GET)
  BakeStatus lookupStatus(@PathVariable("region") String region, @PathVariable("statusId") String statusId) {
    def bakeStatus = bakeStore.retrieveBakeStatusById(statusId)

    if (bakeStatus) {
      return bakeStatus
    }

    throw new IllegalArgumentException("Unable to retrieve status for '$statusId'.")
  }

  @RequestMapping(value = "/{region}/bake/{bakeId}", method = RequestMethod.GET)
  Bake lookupBake(@PathVariable("region") String region, @PathVariable("bakeId") String bakeId) {
    def bake = bakeStore.retrieveBakeDetailsById(bakeId)

    if (bake) {
      return bake
    }

    throw new IllegalArgumentException("Unable to retrieve bake details for '$bakeId'.")
  }

  // TODO(duftler): Synchronize this with existing bakery api.
  @RequestMapping(value = "/{region}/logs/{statusId}", method = RequestMethod.GET)
  String lookupLogs(@PathVariable("region") String region,
                    @PathVariable("statusId") String statusId,
                    @RequestParam(value = "html", defaultValue = "false") Boolean html) {
    Map logsContentMap = bakeStore.retrieveBakeLogsById(statusId)

    if (logsContentMap?.logsContent) {

      return html ? "<pre>$logsContentMap.logsContent</pre>" : logsContentMap.logsContent
    }

    throw new IllegalArgumentException("Unable to retrieve logs for '$statusId'.")
  }

  // TODO(duftler): Synchronize this with existing bakery api.
  @RequestMapping(value = '/{region}/bake', method = RequestMethod.DELETE)
  String deleteBake(@PathVariable("region") String region, @RequestBody BakeRequest bakeRequest) {
    if (!bakeRequest.cloud_provider_type) {
      bakeRequest = bakeRequest.copyWith(cloud_provider_type: defaultCloudProviderType)
    }

    CloudProviderBakeHandler cloudProviderBakeHandler = cloudProviderBakeHandlerRegistry.lookup(bakeRequest.cloud_provider_type)

    if (cloudProviderBakeHandler) {
      def bakeKey = cloudProviderBakeHandler.produceBakeKey(region, bakeRequest)

      if (bakeStore.deleteBakeByKey(bakeKey)) {
        return "Deleted bake '$bakeKey'."
      }

      throw new IllegalArgumentException("Unable to locate bake with key '$bakeKey'.")
    } else {
      throw new IllegalArgumentException("Unknown provider type '$bakeRequest.cloud_provider_type'.")
    }
  }

  // TODO(duftler): Synchronize this with existing bakery api.
  @RequestMapping(value = "/{region}/cancel/{statusId}", method = RequestMethod.GET)
  String cancelBake(@PathVariable("region") String region, @PathVariable("statusId") String statusId) {
    if (bakeStore.cancelBakeById(statusId)) {
      return "Cancelled bake '$statusId'."
    }

    throw new IllegalArgumentException("Unable to locate incomplete bake with id '$statusId'.")
  }

  private BakeStatus queryExistingBakes(String bakeKey) {
    def bakeStatus = bakeStore.retrieveBakeStatusByKey(bakeKey)

    if (!bakeStatus) {
      return null
    } else if (bakeStatus.state == BakeStatus.State.PENDING || bakeStatus.state == BakeStatus.State.RUNNING) {
      return bakeStatus
    } else if (bakeStatus.state == BakeStatus.State.COMPLETED && bakeStatus.result == BakeStatus.Result.SUCCESS) {
      return bakeStatus
    } else {
      return null
    }
  }

}
