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

import com.netflix.spinnaker.orca.api.pipeline.TaskResult
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
import com.netflix.spinnaker.orca.front50.model.Application
import com.netflix.spinnaker.orca.front50.tasks.AbstractFront50Task
import com.netflix.spinnaker.orca.KeelService
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import retrofit.RetrofitError

@Slf4j
@Component
class DeleteApplicationTask extends AbstractFront50Task {
  @Autowired(required = false)
  KeelService keelService

  @Override
  TaskResult performRequest(Application application) {
    Map<String, Object> outputs = [:]

    try {
      def existingApplication = front50Service.get(application.name)
      if (existingApplication) {
        outputs.previousState = existingApplication
        front50Service.delete(application.name)
        try {
          front50Service.deletePermission(application.name)
        } catch (RetrofitError re) {
          if (re.response?.status == 404) {
            return TaskResult.SUCCEEDED
          }
          log.error("Could not delete application permission", re)
          return TaskResult.builder(ExecutionStatus.TERMINAL).outputs(outputs).build()
        }
        // delete Managed Delivery data
        if (keelService != null) {
          log.debug("Deleting Managed Delivery data for {}", application.name)
          keelService.deleteDeliveryConfig(application.name)
        }
      }
    } catch (RetrofitError e) {
      if (e.response?.status == 404) {
        return TaskResult.SUCCEEDED
      }
      log.error("Could not delete application", e)
      return TaskResult.builder(ExecutionStatus.TERMINAL).outputs(outputs).build()
    }
    return TaskResult.builder(ExecutionStatus.SUCCEEDED).outputs(outputs).build()
  }

  @Override
  String getNotificationType() {
    return "deleteapplication"
  }
}
