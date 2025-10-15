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

import com.google.common.annotations.VisibleForTesting
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
import com.netflix.spinnaker.orca.api.pipeline.Task
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution
import com.netflix.spinnaker.orca.api.pipeline.TaskResult
import com.netflix.spinnaker.orca.front50.Front50Service
import com.netflix.spinnaker.orca.api.pipeline.graph.StageDefinitionBuilder
import com.netflix.spinnaker.orca.api.pipeline.graph.TaskNode
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import javax.annotation.Nonnull

@Component
class UpsertProjectStage implements StageDefinitionBuilder {
  @Override
  void taskGraph(@Nonnull StageExecution stage, @Nonnull TaskNode.Builder builder) {
    builder
      .withTask("upsertProject", UpsertProjectTask)
  }

  @Slf4j
  @Component
  @VisibleForTesting
  public static class UpsertProjectTask implements Task {
    @Autowired(required = false)
    Front50Service front50Service

    UpsertProjectTask() {}

    @Nonnull
    @Override
    TaskResult execute(@Nonnull StageExecution stage) {
      if (!front50Service) {
        throw new UnsupportedOperationException("Unable to modify projects, front50 has not been enabled. Fix this by setting front50.enabled: true")
      }

      def projectId = stage.mapTo("/project", Front50Service.Project)
      def project = stage.mapTo("/project", Map)

      if (projectId.id) {
        Retrofit2SyncCall.executeCall(front50Service.updateProject(projectId.id, project))
      } else {
        Retrofit2SyncCall.executeCall(front50Service.createProject(project))
      }

      def outputs = [
        "notification.type": "upsertproject"
      ]

      return TaskResult.builder(ExecutionStatus.SUCCEEDED).context(outputs).build()
    }
  }
}
