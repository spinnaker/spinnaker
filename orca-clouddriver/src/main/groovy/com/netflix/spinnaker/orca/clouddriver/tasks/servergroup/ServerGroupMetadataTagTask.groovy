/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.tasks.servergroup

import java.util.concurrent.TimeUnit
import com.fasterxml.jackson.annotation.JsonProperty
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.RetryableTask
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.clouddriver.KatoService
import com.netflix.spinnaker.orca.clouddriver.model.TaskId
import com.netflix.spinnaker.orca.clouddriver.tasks.AbstractCloudProviderAwareTask
import com.netflix.spinnaker.orca.pipeline.model.Orchestration
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
@Slf4j
class ServerGroupMetadataTagTask extends AbstractCloudProviderAwareTask implements RetryableTask {
  long backoffPeriod = TimeUnit.SECONDS.toMillis(5)
  long timeout = TimeUnit.MINUTES.toMillis(5)

  @Autowired
  KatoService kato

  @Override
  TaskResult execute(Stage stage) {
    try {
      TaskId taskId = kato.requestOperations(buildTagOperations(stage)).toBlocking().first()
      return new TaskResult(ExecutionStatus.SUCCEEDED, new HashMap<String, Object>() {
        {
          put("notification.type", "upsertentitytags")
          put("kato.last.task.id", taskId)
        }
      })
    } catch (Exception e) {
      log.error("Failed to tag deployed server groups (stageId: ${stage.id}, executionId: ${stage.execution.id})", e)
      return new TaskResult(ExecutionStatus.FAILED_CONTINUE)
    }
  }

  private List<Map> buildTagOperations(Stage stage) {
    def tag = [
      name : "spinnaker:metadata",
      value: [
        "stageId"      : stage.id,
        "executionId"  : stage.execution.id,
        "executionType": stage.execution.class.simpleName.toLowerCase(),
        "application"  : stage.execution.application,
        "user"         : stage.execution.authentication?.user
      ]
    ]

    if (stage.execution instanceof Orchestration) {
      tag.value.description = ((Orchestration) stage.execution).description
    } else if (stage.execution instanceof Pipeline) {
      Pipeline pipeline = (Pipeline) stage.execution
      if (pipeline.name) {
        tag.value.description = pipeline.name
      }
      tag.value.pipelineConfigId = pipeline.pipelineConfigId
    }

    if (stage.context.reason || stage.context.comments) {
      tag.value.comments = stage.context.comments ?: stage.context.reason
    }

    def operations = []
    ((StageData) stage.mapTo(StageData)).deployServerGroups.each { String region, Set<String> serverGroups ->
      serverGroups.each { String serverGroup ->
        operations <<
          [
            "upsertEntityTags": [
              tags     : [tag],
              entityRef: [
                entityType   : "servergroup",
                entityId     : serverGroup,
                account      : getCredentials(stage),
                region       : region,
                cloudProvider: getCloudProvider(stage)
              ]
            ]
          ]
      }
    }

    return operations
  }

  static class StageData {
    @JsonProperty("deploy.server.groups")
    Map<String, Set<String>> deployServerGroups = [:]
  }
}
