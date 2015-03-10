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

package com.netflix.spinnaker.rosco.executor

import com.netflix.spinnaker.rosco.api.Bake
import com.netflix.spinnaker.rosco.api.BakeStatus
import com.netflix.spinnaker.rosco.config.RoscoConfiguration
import com.netflix.spinnaker.rosco.persistence.BakeStore
import com.netflix.spinnaker.rosco.providers.registry.CloudProviderBakeHandlerRegistry
import com.netflix.spinnaker.rosco.rush.api.RushService
import com.netflix.spinnaker.rosco.rush.api.ScriptExecution
import com.netflix.spinnaker.rosco.rush.api.ScriptRequest
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.ApplicationListener
import org.springframework.context.event.ContextRefreshedEvent
import org.springframework.stereotype.Component
import retrofit.RetrofitError
import retrofit.mime.TypedByteArray
import rx.functions.Action0
import rx.schedulers.Schedulers

import java.util.concurrent.TimeUnit

/**
 * BakePoller periodically queries the bake store for incomplete bakes. For each incomplete bake, it queries
 * the scripting engine for an up-to-date status and logs. The status and logs are then persisted via the bake
 * store. When a bake completes, it is the BakePoller that persists the completed bake details via the bake store.
 * The polling interval defaults to 15 seconds and can be overridden by specifying the pollingIntervalSeconds
 * property.
 */
@Slf4j
@Component
class BakePoller implements ApplicationListener<ContextRefreshedEvent> {

  @Value('${pollingIntervalSeconds:15}')
  int pollingIntervalSeconds

  @Autowired
  BakeStore bakeStore

  @Autowired
  ScriptRequest baseScriptRequest

  @Autowired
  RushService rushService

  @Autowired
  CloudProviderBakeHandlerRegistry cloudProviderBakeHandlerRegistry

  @Autowired
  RoscoConfiguration.ExecutionStatusToBakeStateMap executionStatusToBakeStateMap

  @Autowired
  RoscoConfiguration.ExecutionStatusToBakeResultMap executionStatusToBakeResultMap

  @Override
  void onApplicationEvent(ContextRefreshedEvent event) {
    Schedulers.io().createWorker().schedulePeriodically(
      {
        try {
          rx.Observable.from(bakeStore.incompleteBakeIds)
            .subscribe(
            { String statusId ->
              updateBakeStatusAndLogs(statusId)
            },
            {
              log.error("Error: ${it.message}")
            },
            {} as Action0
          )
        } catch (Exception e) {
          log.error("Polling Error:", e)
        }
      } as Action0, 0, pollingIntervalSeconds, TimeUnit.SECONDS
    )
  }

  void updateBakeStatusAndLogs(String statusId) {
    try {
      ScriptExecution scriptExecution = rushService.scriptDetails(statusId).toBlocking().single()
      Map logsContentMap = rushService.getLogs(statusId, baseScriptRequest).toBlocking().single()
      BakeStatus.State state = executionStatusToBakeStateMap.convertExecutionStatusToBakeState(scriptExecution.status)

      if (state == BakeStatus.State.COMPLETED) {
        completeBake(statusId, logsContentMap?.logsContent)
      }

      bakeStore.updateBakeStatus(new BakeStatus(id: scriptExecution.id,
                                                resource_id: scriptExecution.id,
                                                state: state,
                                                result: executionStatusToBakeResultMap.convertExecutionStatusToBakeResult(scriptExecution.status)),
                                 logsContentMap)
    } catch (RetrofitError e) {
      handleRetrofitError(e, "Unable to retrieve status for '$statusId'.", statusId)

      bakeStore.updateBakeStatus(new BakeStatus(id: statusId,
                                                resource_id: statusId,
                                                state: BakeStatus.State.CANCELLED,
                                                result: BakeStatus.Result.FAILURE))
    }
  }

  void completeBake(String bakeId, String logsContent) {
    if (logsContent) {
      int endOfFirstLineIndex = logsContent.indexOf("\n")

      if (endOfFirstLineIndex > -1) {
        def logsContentFirstLine = logsContent.substring(0, endOfFirstLineIndex)
        def cloudProviderBakeHandler = cloudProviderBakeHandlerRegistry.findProducer(logsContentFirstLine)

        if (cloudProviderBakeHandler) {
          String region = bakeStore.retrieveRegionById(bakeId)

          if (region) {
            Bake bakeDetails = cloudProviderBakeHandler.scrapeCompletedBakeResults(region, bakeId, logsContent)

            bakeStore.updateBakeDetails(bakeDetails)

            return
          }
        }
      }
    }

    log.error("Unable to retrieve bake details for '$bakeId'.")
  }

  private handleRetrofitError(RetrofitError e, String errMessage, String id) {
    log.error(errMessage, e)

    def errorBytes = ((TypedByteArray)e?.response?.body)?.bytes
    def errorMessage = errorBytes ? new String(errorBytes) : "{}"

    bakeStore.storeBakeError(id, errorMessage)
  }
}
