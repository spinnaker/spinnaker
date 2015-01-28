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

package com.netflix.spinnaker.orca.kato.tasks.scalingprocess

import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.kato.api.KatoService
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.beans.factory.annotation.Autowired

abstract class AbstractScalingProcessTask implements Task {
  @Autowired
  KatoService katoService

  abstract String getType()

  @Override
  TaskResult execute(Stage stage) {
    def taskId = katoService.requestOperations([[(getType()): new HashMap(stage.context)]])
      .toBlocking().first()

    def stageData = stage.mapTo(StageData)

    return new DefaultTaskResult(ExecutionStatus.SUCCEEDED, [
      "notification.type"     : getType().toLowerCase(),
      "deploy.server.groups"  : stageData.affectedServerGroupMap,
      "kato.last.task.id"     : taskId,
      "kato.task.id"          : taskId, // TODO retire this.
    ])
  }

  static class StageData {
    List<String> regions
    String asgName

    Map<String, List<String>> getAffectedServerGroupMap() {
      regions.collectEntries {
        [(it): [asgName]]
      }
    }
  }
}
