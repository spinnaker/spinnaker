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

package com.netflix.spinnaker.orca.applications.tasks

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerHttpException
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerServerException
import com.netflix.spinnaker.orca.api.pipeline.TaskResult
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
import com.netflix.spinnaker.orca.front50.Front50Service
import com.netflix.spinnaker.orca.front50.model.Application
import com.netflix.spinnaker.orca.front50.tasks.AbstractFront50Task
import com.netflix.spinnaker.orca.KeelService
import groovy.util.logging.Slf4j
import org.springframework.lang.Nullable
import org.springframework.stereotype.Component

@Slf4j
@Component
class DeleteApplicationTask extends AbstractFront50Task {
  KeelService keelService

  DeleteApplicationTask(@Nullable Front50Service front50Service,
                        @Nullable KeelService keelService,
                        ObjectMapper mapper,
                        DynamicConfigService configService
  ) {
    super(front50Service, mapper, configService)
    this.keelService = keelService
  }

  @Override
  long getBackoffPeriod() {
    return this.configService
        .getConfig(
            Long.class,
            "tasks.delete-application.backoff-ms",
            10000L
        )
  }

  @Override
  long getTimeout() {
    return this.configService
        .getConfig(
            Long.class,
            "tasks.delete-application.timeout-ms",
            3600000L
        )
  }

  @Override
  TaskResult performRequest(Application application) {
    Map<String, Object> outputs = [:]

    try {
      def existingApplication = Retrofit2SyncCall.execute(front50Service.get(application.name))
      if (existingApplication) {
        outputs.previousState = existingApplication
        Retrofit2SyncCall.executeCall(front50Service.delete(application.name))
        try {
          Retrofit2SyncCall.executeCall(front50Service.deletePermission(application.name))
        } catch (SpinnakerHttpException re) {
          if (re.responseCode == 404) {
            return TaskResult.SUCCEEDED
          }
          log.error("Could not delete application permission", re)
          return TaskResult.builder(ExecutionStatus.TERMINAL).outputs(outputs).build()
        } catch (SpinnakerServerException e){
          log.error("Could not delete application permission", e)
          return TaskResult.builder(ExecutionStatus.TERMINAL).outputs(outputs).build()
        }
        // delete Managed Delivery data
        if (keelService != null) {
          log.debug("Deleting Managed Delivery data for {}", application.name)
          Retrofit2SyncCall.executeCall(keelService.deleteDeliveryConfig(application.name))
        }
      }
    } catch (SpinnakerHttpException httpException){
      if (httpException.responseCode == 404) {
        return TaskResult.SUCCEEDED
      }
      log.error("Could not delete application", httpException)
      return TaskResult.builder(ExecutionStatus.TERMINAL).outputs(outputs).build()
    } catch (SpinnakerServerException serverException) {
      log.error("Could not delete application", serverException)
      return TaskResult.builder(ExecutionStatus.TERMINAL).outputs(outputs).build()
    }
    return TaskResult.builder(ExecutionStatus.SUCCEEDED).outputs(outputs).build()
  }

  @Override
  String getNotificationType() {
    return "deleteapplication"
  }
}
