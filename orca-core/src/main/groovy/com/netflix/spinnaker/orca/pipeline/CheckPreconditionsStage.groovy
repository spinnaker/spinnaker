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

package com.netflix.spinnaker.orca.pipeline

import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.tasks.PreconditionTask
import org.springframework.batch.core.Step
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class CheckPreconditionsStage extends ParallelStage implements StepProvider {
  static final String PIPELINE_CONFIG_TYPE = "checkPreconditions"

  @Autowired
  List<PreconditionTask> preconditionTasks

  CheckPreconditionsStage() {
    super(PIPELINE_CONFIG_TYPE)
  }

  @Override
  List<Step> buildSteps(Stage stage) {
    String preconditionType = stage.context.preconditionType
    if (!preconditionType) {
      throw new IllegalStateException("no preconditionType specified for stage $stage.id")
    }
    Task preconditionTask = preconditionTasks.find { it.preconditionType == preconditionType }
    if (!preconditionTask) {
      throw new IllegalStateException("no Precondition implementation for type $preconditionType")
    }
    [buildStep(stage, "checkPrecondition", preconditionTask)]
  }

  @Override
  List<Step> buildParallelContextSteps(Stage stage) {
    buildSteps(stage)
  }

  @Override
  String parallelStageName(Stage stage, boolean hasParallelFlows) {
    "Check Preconditions"
  }

  @Override
  Task completeParallel() {
    new Task() {
      @Override
      TaskResult execute(Stage stage) {
        DefaultTaskResult.SUCCEEDED
      }
    }
  }

  @Override
  List<Map<String, Object>> parallelContexts(Stage stage) {
    def baseContext = new HashMap(stage.context)
    List<Map> preconditions = baseContext.remove('preconditions') as List<Map>
    return preconditions.collect { preconditionConfig ->
      def context = baseContext + preconditionConfig + [type: PIPELINE_CONFIG_TYPE]
      context.name = context.name ?: "Check precondition $context.preconditionType".toString()
      return context
    }
  }
}
