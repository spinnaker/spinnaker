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

package com.netflix.spinnaker.orca.kato.pipeline.strategy

import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.kato.pipeline.support.SourceResolver
import com.netflix.spinnaker.orca.kato.pipeline.support.StageData
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class DetermineSourceServerGroupTask implements Task {

  @Autowired
  SourceResolver sourceResolver

  @Override
  TaskResult execute(Stage stage) {
    def source = sourceResolver.getSource(stage)
    def stageOutputs = [:]
    if (source) {
      stageOutputs.source = [
        asgName          : source.asgName,
        account          : source.account,
        region           : source.region,
        useSourceCapacity: useSourceCapacity(stage, source)
      ]
    }
    new DefaultTaskResult(ExecutionStatus.SUCCEEDED, stageOutputs)
  }

  Boolean useSourceCapacity(Stage stage, StageData.Source source) {
    if (source.useSourceCapacity != null) return source.useSourceCapacity
    if (stage.context.useSourceCapacity != null) return (stage.context.useSourceCapacity as Boolean)
    return null
  }
}
