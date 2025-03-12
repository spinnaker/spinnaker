/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.clouddriver.aws.deploy.ops

import com.amazonaws.services.cloudwatch.model.DeleteAlarmsRequest
import com.netflix.spinnaker.clouddriver.aws.deploy.description.DeleteAlarmDescription
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import org.springframework.beans.factory.annotation.Autowired

class DeleteAlarmAtomicOperation implements AtomicOperation<Void> {

  private static final String BASE_PHASE = "DELETE_ALARM"

  private static Task getTask() {
      TaskRepository.threadLocalTask.get()
  }

  @Autowired
  AmazonClientProvider amazonClientProvider

  private final DeleteAlarmDescription description

  DeleteAlarmAtomicOperation(DeleteAlarmDescription description) {
        this.description = description
    }

    @Override
    Void operate(List priorOutputs) {
      task.updateStatus BASE_PHASE, "Initializing Delete Alarm Operation..."
      def cloudWatch = amazonClientProvider.getCloudWatch(description.credentials, description.region, true)
      String alarmDescription = "${description.names.join(", ")} in ${description.region} for ${description.credentials.name}"
      task.updateStatus BASE_PHASE, "Deleting ${alarmDescription}."
      cloudWatch.deleteAlarms(new DeleteAlarmsRequest(
        alarmNames: description.names
      ))
      task.updateStatus BASE_PHASE, "Done deleting ${alarmDescription}."
      null
    }
}
