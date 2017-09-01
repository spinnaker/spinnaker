/*
 * Copyright 2017 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.kayenta.pipeline

import com.netflix.spinnaker.orca.CancellableStage
import com.netflix.spinnaker.orca.kayenta.KayentaService
import com.netflix.spinnaker.orca.kayenta.tasks.MonitorCanaryTask
import com.netflix.spinnaker.orca.kayenta.tasks.RunCanaryTask
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder
import com.netflix.spinnaker.orca.pipeline.TaskNode
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
@Slf4j
class RunCanaryPipelineStage implements StageDefinitionBuilder, CancellableStage {

  public static final STAGE_TYPE = "runCanary"

  @Autowired
  KayentaService kayentaService

  @Override
  <T extends Execution<T>> void taskGraph(Stage<T> stage, TaskNode.Builder builder) {
    builder
      .withTask("runCanary", RunCanaryTask)
      .withTask("monitorCanary", MonitorCanaryTask)
  }

  @Override
  String getType() {
    STAGE_TYPE
  }

  @Override
  CancellableStage.Result cancel(Stage stage) {
    Map<String, Object> context = stage.getContext()
    String canaryPipelineExecutionId = (String)context.get("canaryPipelineExecutionId")

    log.info("Cancelling stage (stageId: $stage.id: executionId: $stage.execution.id, canaryPipelineExecutionId: $canaryPipelineExecutionId, context: $stage.context)")

    try {
      kayentaService.cancelPipelineExecution(canaryPipelineExecutionId, "")
    } catch (Exception e) {
      log.error("Failed to cancel stage (stageId: $stage.id, executionId: $stage.execution.id), e: $e.message", e)
    }

    return new CancellableStage.Result(stage, [:])
  }
}
