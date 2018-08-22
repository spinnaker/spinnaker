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

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.front50.model.Application
import com.netflix.spinnaker.orca.front50.tasks.AbstractFront50Task
import groovy.util.logging.Slf4j
import org.springframework.stereotype.Component
import retrofit.RetrofitError

@Slf4j
@Component
class DeleteApplicationTask extends AbstractFront50Task {
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
            return new TaskResult(ExecutionStatus.SUCCEEDED, [:], [:])
          }
          log.error("Could not create or update application permission", re)
          return new TaskResult(ExecutionStatus.TERMINAL, [:], outputs)
        }
      }
    } catch (RetrofitError e) {
      if (e.response?.status == 404) {
        return new TaskResult(ExecutionStatus.SUCCEEDED, [:], [:])
      }
      log.error("Could not create or update application permission", e)
      return new TaskResult(ExecutionStatus.TERMINAL, [:], outputs)
    }
    return new TaskResult(ExecutionStatus.SUCCEEDED, [:], outputs)
  }

  @Override
  String getNotificationType() {
    return "deleteapplication"
  }
}
