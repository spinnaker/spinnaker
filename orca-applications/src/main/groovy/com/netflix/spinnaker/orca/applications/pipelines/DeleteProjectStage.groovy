/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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


package com.netflix.spinnaker.orca.applications.pipelines

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.front50.Front50Service
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder
import com.netflix.spinnaker.orca.pipeline.TaskNode
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class DeleteProjectStage implements StageDefinitionBuilder {
  @Override
  void taskGraph(Stage stage, TaskNode.Builder builder) {
    builder
      .withTask("deleteProject", DeleteProjectTask)
  }

  @Component
  static class DeleteProjectTask implements Task {
    @Autowired(required = false)
    Front50Service front50Service

    @Override
    TaskResult execute(Stage stage) {
      if (!front50Service) {
        throw new UnsupportedOperationException("Unable to modify projects, front50 has not been enabled. Fix this by setting front50.enabled: true")
      }
      def projectId = stage.mapTo("/project", Front50Service.Project)
      front50Service.deleteProject(projectId.id)

      def outputs = [
        "notification.type": "deleteproject"
      ]

      return TaskResult.builder(ExecutionStatus.SUCCEEDED).context(outputs).build()
    }
  }
}
