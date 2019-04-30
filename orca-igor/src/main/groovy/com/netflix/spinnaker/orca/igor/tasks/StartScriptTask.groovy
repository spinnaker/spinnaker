/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.orca.igor.tasks

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.igor.BuildService
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class StartScriptTask implements Task {

  @Autowired
  BuildService buildService

  @Autowired
  ObjectMapper objectMapper

  @Value('${script.master:master}')
  String master

  @Value('${script.job:job}')
  String defaultJob

  @Override
  TaskResult execute(Stage stage) {
    String scriptPath = stage.context.scriptPath
    String command = stage.context.command
    String image = stage.context.image
    String region = stage.context.region
    String account = stage.context.account
    String cluster = stage.context.cluster
    String cmc = stage.context.cmc
    String repoUrl = stage.context.repoUrl
    String repoBranch = stage.context.repoBranch
    String job = stage.context.job ?: defaultJob

    if (stage.execution.trigger.strategy) {
      def trigger = stage.execution.trigger
      image = image ?: trigger.parameters.amiName ?: trigger.parameters.imageId ?: ''
      cluster = cluster ?: trigger.parameters.cluster ?: ''
      account = account ?: trigger.parameters.credentials ?: ''
      region = region ?: trigger.parameters.region ?: trigger.parameters.zone ?: ''
    }

    def parameters = [
      SCRIPT_PATH  : scriptPath,
      COMMAND      : command,
      IMAGE_ID     : image,
      REGION_PARAM : region,
      ENV_PARAM    : account,
      CLUSTER_PARAM: cluster,
      CMC          : cmc
    ]

    if (repoUrl) {
      parameters.REPO_URL = repoUrl
    }

    if (repoBranch) {
      parameters.REPO_BRANCH = repoBranch
    }

    String queuedBuild = buildService.build(master, job, parameters)
    TaskResult.builder(ExecutionStatus.SUCCEEDED).context([master: master, job: job, queuedBuild: queuedBuild]).build()
  }

}
