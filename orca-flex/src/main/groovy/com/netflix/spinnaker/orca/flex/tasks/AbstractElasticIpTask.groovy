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

package com.netflix.spinnaker.orca.flex.tasks

import com.netflix.frigga.Names
import com.netflix.spinnaker.moniker.Moniker
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.flex.FlexService
import com.netflix.spinnaker.orca.flex.model.ElasticIpRequest
import com.netflix.spinnaker.orca.flex.model.ElasticIpResult
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Autowired

@CompileStatic
abstract class AbstractElasticIpTask implements Task {
  @Autowired(required = false)
  FlexService flexService

  abstract ElasticIpResult performRequest(StageData stageData)

  abstract String getNotificationType()

  @Override
  TaskResult execute(Stage stage) {
    def outputs = [
      "notification.type"    : getNotificationType(),
      "elastic.ip.assignment": performRequest(stage.mapTo(StageData))
    ]

    return TaskResult.builder(ExecutionStatus.SUCCEEDED).context(outputs).build()
  }

  static class StageData {
    String account
    String cluster
    Moniker moniker
    String region
    ElasticIpRequest elasticIp

    String getApplication() {
      return moniker ? moniker.getApp() : cluster ? Names.parseName(cluster).app : null
    }
  }
}
