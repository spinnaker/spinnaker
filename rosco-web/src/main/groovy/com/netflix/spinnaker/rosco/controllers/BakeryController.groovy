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

import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.rosco.api.Bake
import com.netflix.spinnaker.rosco.api.BakeOptions
import com.netflix.spinnaker.rosco.api.BakeRequest
import com.netflix.spinnaker.rosco.api.BakeStatus
import com.netflix.spinnaker.rosco.jobs.BakeRecipe
import com.netflix.spinnaker.rosco.jobs.JobExecutor
import com.netflix.spinnaker.rosco.jobs.JobRequest
import com.netflix.spinnaker.rosco.persistence.BakeStore
import com.netflix.spinnaker.rosco.providers.CloudProviderBakeHandler
import com.netflix.spinnaker.rosco.providers.registry.CloudProviderBakeHandlerRegistry
import com.netflix.spinnaker.security.AuthenticatedRequest
import groovy.transform.InheritConstructors
import groovy.util.logging.Slf4j
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import org.springframework.web.method.annotation.RequestHeaderMapMethodArgumentResolver

import java.util.concurrent.TimeUnit

@RestController
@Slf4j
class BakeryController {

  @Autowired
  BakeStore bakeStore

  @Autowired
  JobExecutor jobExecutor

  @Autowired
  CloudProviderBakeHandlerRegistry cloudProviderBakeHandlerRegistry

  @Autowired
  Registry registry

  @Value('${defaultCloudProviderType:aws}')
  BakeRequest.CloudProviderType defaultCloudProviderType

  @Value('${rosco.polling.waitForJobStartTimeoutMillis:5000}')
  long waitForJobStartTimeoutMillis

  @Value('${rosco.polling.waitForJobStartPollingIntervalMillis:500}')
  long waitForJobStartPollingIntervalMillis

  @RequestMapping(value = '/bakeOptions', method = RequestMethod.GET)
  List<BakeOptions> bakeOptions() {
    cloudProviderBakeHandlerRegistry.list().collect { it.getBakeOptions() }
  }

  @RequestMapping(value = '/bakeOptions/{cloudProvider}', method = RequestMethod.GET)
  BakeOptions bakeOptionsByCloudProvider(@PathVariable("cloudProvider") BakeRequest.CloudProviderType cloudProvider) {
    def bakeHandler = cloudProviderBakeHandlerRegistry.lookup(cloudProvider)
    if (!bakeHandler) {
      throw new BakeOptions.Exception("Cloud provider $cloudProvider not found")
    }
    return bakeHandler.getBakeOptions()
  }

  @RequestMapping(value = '/bakeOptions/{cloudProvider}/baseImages/{imageId}', method = RequestMethod.GET)
  BakeOptions.BaseImage baseImage(@PathVariable("cloudProvider") BakeRequest.CloudProviderType cloudProvider, @PathVariable("imageId") String imageId) {
    BakeOptions bakeOptions = bakeOptionsByCloudProvider(cloudProvider)
    def baseImage = bakeOptions.baseImages.find { it.id == imageId }
    if (!baseImage) {
      def images = bakeOptions.baseImages*.id.join(", ")
      throw new BakeOptions.Exception("Can't find base image with id ${imageId} in ${cloudProvider} base images: ${images}")
    }
    return baseImage
  }

  @ExceptionHandler(BakeOptions.Exception)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  Map handleBakeOptionsException(BakeOptions.Exception e) {
    [error: "bake.options.not.found", status: HttpStatus.NOT_FOUND, messages: ["Bake options not found. " + e.message]]
  }

  private BakeStatus runBake(String bakeKey, String region, BakeRecipe bakeRecipe, BakeRequest bakeRequest, JobRequest jobRequest) {
    String jobId = jobExecutor.startJob(jobRequest)

    // Give the job jobExecutor some time to kick off the job.
    // Poll for bake status by job id every 1/2 second for 5 seconds.
    // The goal here is to fail fast. If it takes too much time, no point in waiting here.
    def startTime = System.currentTimeMillis()

    while (System.currentTimeMillis() - startTime < waitForJobStartTimeoutMillis) {
      def bakeStatus = jobExecutor.jobExists(jobId)

      if (bakeStatus) {
        break
      } else {
        sleep(waitForJobStartPollingIntervalMillis)
      }
    }

    // Update the status right away so we can fail fast if necessary.
    BakeStatus newBakeStatus = jobExecutor.updateJob(jobId)

    if (!newBakeStatus) {
      throw new IllegalArgumentException("Unable to locate bake with id '$jobId'. Currently, " +
        "${waitForJobStartTimeoutMillis}ms is the configured timeout for the job to start. If it is a persistent issue, " +
        "you could increase 'rosco.polling.waitForJobStartTimeoutMillis' to give the job more time to start.")
    }

    if (newBakeStatus.result == BakeStatus.Result.FAILURE && newBakeStatus.logsContent) {
      throw new IllegalArgumentException(newBakeStatus.logsContent)

      // If we don't have logs content to return here, just let the poller try again on the next iteration.
    }

    // Ok, it didn't fail right away; the bake is underway.
    BakeStatus returnedBakeStatus = bakeStore.storeNewBakeStatus(bakeKey,
                                                                 region,
                                                                 bakeRecipe,
                                                                 bakeRequest,
                                                                 newBakeStatus,
                                                                 jobRequest.tokenizedCommand.join(" "))

    // Check if the script returned a bake status set by the winner of a race.
    if (returnedBakeStatus.id != newBakeStatus.id) {
      // Kill the new sub-process.
      jobExecutor.cancelJob(newBakeStatus.id)
    }

    return returnedBakeStatus
  }

  @RequestMapping(value = '/api/v1/{region}/bake', method = RequestMethod.POST)
  BakeStatus createBake(@PathVariable("region") String region,
                        @RequestBody BakeRequest bakeRequest,
                        @RequestParam(value = "rebake", defaultValue = "0") String rebake) {
    String executionId = AuthenticatedRequest.getSpinnakerExecutionId().orElse(null)

    if (!bakeRequest.cloud_provider_type) {
      bakeRequest = bakeRequest.copyWith(
        cloud_provider_type: defaultCloudProviderType,
        spinnaker_execution_id: executionId
      )
    } else if (executionId != null) {
      bakeRequest = bakeRequest.copyWith(spinnaker_execution_id: executionId)
    }

    CloudProviderBakeHandler cloudProviderBakeHandler = cloudProviderBakeHandlerRegistry.lookup(bakeRequest.cloud_provider_type)

    if (cloudProviderBakeHandler) {
      def bakeKey = cloudProviderBakeHandler.produceBakeKey(region, bakeRequest)

      if (rebake == "1") {
        registry.counter(registry.createId("bakesRequested", [flavor: "rebake"])).increment()

        String bakeId = bakeStore.deleteBakeByKeyPreserveDetails(bakeKey)

        if (bakeId) {
          jobExecutor.cancelJob(bakeId)
        }
      } else {
        def existingBakeStatus = queryExistingBakes(bakeKey)

        if (existingBakeStatus) {
          registry.counter(registry.createId("bakesRequested", [flavor: "duplicate"])).increment()

          return existingBakeStatus
        } else {
          registry.counter(registry.createId("bakesRequested", [flavor: "plain"])).increment()
        }
      }

      def bakeRecipe = cloudProviderBakeHandler.produceBakeRecipe(region, bakeRequest)
      def jobRequest = new JobRequest(tokenizedCommand: bakeRecipe.command,
                                      maskedParameters: cloudProviderBakeHandler.maskedPackerParameters,
                                      jobId: bakeRequest.request_id,
                                      executionId: bakeRequest.spinnaker_execution_id)

      if (bakeStore.acquireBakeLock(bakeKey)) {
        return runBake(bakeKey, region, bakeRecipe, bakeRequest, jobRequest)
      } else {
        def startTime = System.currentTimeMillis()

        // Poll for bake status by bake key every 1/2 second for 5 seconds.
        while (System.currentTimeMillis() - startTime < waitForJobStartTimeoutMillis) {
          def bakeStatus = bakeStore.retrieveBakeStatusByKey(bakeKey)

          if (bakeStatus) {
            return bakeStatus
          } else {
            sleep(waitForJobStartPollingIntervalMillis)
          }
        }

        // Maybe the TTL expired but the bake status wasn't set for some other reason? Let's try again before giving up.
        if (bakeStore.acquireBakeLock(bakeKey)) {
          return runBake(bakeKey, region, bakeRecipe, bakeRequest, jobRequest)
        }

        throw new IllegalArgumentException("Unable to acquire lock and unable to determine id of lock holder for bake " +
          "key '$bakeKey'.")
      }
    } else {
      throw new IllegalArgumentException("Unknown provider type '$bakeRequest.cloud_provider_type'.")
    }
  }

  @ApiOperation(value = "Look up bake request status")
  @RequestMapping(value = "/api/v1/{region}/status/{statusId}", method = RequestMethod.GET)
  BakeStatus lookupStatus(@ApiParam(value = "The region of the bake request to lookup", required = true) @PathVariable("region") String region,
                          @ApiParam(value = "The id of the bake request to lookup", required = true) @PathVariable("statusId") String statusId) {
    def bakeStatus = bakeStore.retrieveBakeStatusById(statusId)

    if (bakeStatus) {
      return bakeStatus
    }

    throw new IllegalArgumentException("Unable to retrieve status for '$statusId'.")
  }

  @ApiOperation(value = "Look up bake details")
  @RequestMapping(value = "/api/v1/{region}/bake/{bakeId}", method = RequestMethod.GET)
  Bake lookupBake(@ApiParam(value = "The region of the bake to lookup", required = true) @PathVariable("region") String region,
                  @ApiParam(value = "The id of the bake to lookup", required = true) @PathVariable("bakeId") String bakeId) {
    def bake = bakeStore.retrieveBakeDetailsById(bakeId)

    if (bake) {
      return bake
    }

    throw new IllegalArgumentException("Unable to retrieve bake details for '$bakeId'.")
  }

  @RequestMapping(value = "/api/v1/{region}/logs/{statusId}", produces = ["application/json"], method = RequestMethod.GET)
  Map lookupLogs(@PathVariable("region") String region, @PathVariable("statusId") String statusId) {
    Map logsContentMap = bakeStore.retrieveBakeLogsById(statusId)

    if (logsContentMap?.logsContent) {
      return logsContentMap
    } else {
      throw new LogsNotFoundException("Unable to retrieve logs for '$statusId'.")
    }
  }

  @RequestMapping(value = "/api/v1/{region}/logs/image/{imageId}", produces = ["application/json"], method = RequestMethod.GET)
  Map lookupLogsByImageId(@PathVariable("region") String region, @PathVariable("imageId") String imageId) {
    def bakeId = bakeStore.getBakeIdFromImage(region, imageId)
    if (!bakeId) {
      throw new LogsNotFoundException("Unable to retrieve logs for image id '$imageId'.")
    }
    Map<String, String> logsContentMap = bakeStore.retrieveBakeLogsById(bakeId)

    if (logsContentMap?.logsContent) {
      return logsContentMap
    } else {
      throw new LogsNotFoundException("Unable to retrieve logs for '$bakeId'.")
    }
  }

  @InheritConstructors
  @ResponseStatus(value = HttpStatus.NOT_FOUND, reason = "Logs not found.")
  static class LogsNotFoundException extends RuntimeException {}

  // TODO(duftler): Synchronize this with existing bakery api.
  @RequestMapping(value = '/api/v1/{region}/bake', method = RequestMethod.DELETE)
  String deleteBake(@PathVariable("region") String region,
                    @RequestBody BakeRequest bakeRequest) {
    if (!bakeRequest.cloud_provider_type) {
      bakeRequest = bakeRequest.copyWith(cloud_provider_type: defaultCloudProviderType)
    }

    CloudProviderBakeHandler cloudProviderBakeHandler = cloudProviderBakeHandlerRegistry.lookup(bakeRequest.cloud_provider_type)

    if (cloudProviderBakeHandler) {
      def bakeKey = cloudProviderBakeHandler.produceBakeKey(region, bakeRequest)
      def bakeId = bakeStore.deleteBakeByKey(bakeKey)

      if (bakeId) {
        return "Deleted bake '$bakeKey' with id '$bakeId'."
      }

      throw new IllegalArgumentException("Unable to locate bake with key '$bakeKey'.")
    } else {
      throw new IllegalArgumentException("Unknown provider type '$bakeRequest.cloud_provider_type'.")
    }
  }

  // TODO(duftler): Synchronize this with existing bakery api.
  @ApiOperation(value = "Cancel bake request")
  @RequestMapping(value = "/api/v1/{region}/cancel/{statusId}", method = RequestMethod.GET)
  String cancelBake(@ApiParam(value = "The region of the bake request to cancel", required = true) @PathVariable("region") String region,
                    @ApiParam(value = "The id of the bake request to cancel", required = true) @PathVariable("statusId") String statusId) {
    if (bakeStore.cancelBakeById(statusId)) {
      jobExecutor.cancelJob(statusId)

      // This will have the most up-to-date timestamp.
      BakeStatus bakeStatus = bakeStore.retrieveBakeStatusById(statusId)
      long millis = bakeStatus.updatedTimestamp - bakeStatus.createdTimestamp
      registry.timer(registry.createId("bakesCompleted", [success: "false", cause: "explicitlyCanceled"])).record(millis, TimeUnit.MILLISECONDS)

      return "Canceled bake '$statusId'."
    }

    throw new IllegalArgumentException("Unable to locate incomplete bake with id '$statusId'.")
  }

  private BakeStatus queryExistingBakes(String bakeKey) {
    def bakeStatus = bakeStore.retrieveBakeStatusByKey(bakeKey)

    if (!bakeStatus) {
      return null
    } else if (bakeStatus.state == BakeStatus.State.RUNNING) {
      return bakeStatus
    } else if (bakeStatus.state == BakeStatus.State.COMPLETED && bakeStatus.result == BakeStatus.Result.SUCCESS) {
      return bakeStatus
    } else {
      return null
    }
  }

}
