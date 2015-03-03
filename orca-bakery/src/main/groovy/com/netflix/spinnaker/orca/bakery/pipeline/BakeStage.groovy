/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.orca.bakery.pipeline

import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.pipeline.ParallelStage
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.transform.CompileStatic
import com.netflix.spinnaker.orca.bakery.tasks.CompletedBakeTask
import com.netflix.spinnaker.orca.bakery.tasks.CreateBakeTask
import com.netflix.spinnaker.orca.bakery.tasks.MonitorBakeTask
import groovy.util.logging.Slf4j
import org.springframework.batch.core.Step
import org.springframework.stereotype.Component

@Slf4j
@Component
@CompileStatic
class BakeStage extends ParallelStage {

  public static final String MAYO_CONFIG_TYPE = "bake"

  BakeStage() {
    super(MAYO_CONFIG_TYPE)
  }

  @Override
  List<Step> buildSteps(Stage stage) {
    def step1 = buildStep(stage, "createBake", CreateBakeTask)
    def step2 = buildStep(stage, "monitorBake", MonitorBakeTask)
    def step3 = buildStep(stage, "completedBake", CompletedBakeTask)
    [step1, step2, step3]
  }

  @Override
  List<Map<String, Object>> parallelContexts(Stage stage) {
    def deployRegions = stage.context.region ? [stage.context.region] as Set<String> : []
    stage.execution.stages.findAll { it.type == "deploy" }.each {
      def context = it.context.cluster ?: it.context
      deployRegions.addAll((context["availabilityZones"] as Map<String, List<String>>).keySet())
    }

    log.info("Preparing package `${stage.context.package}` for bake in ${deployRegions.join(", ")}")
    if (!stage.context.amiSuffix) {
      stage.context.amiSuffix = now().format("yyyyMMddHHmm", TimeZone.getTimeZone("UTC"))
    }
    return deployRegions.collect {
      stage.context + ([
        type: MAYO_CONFIG_TYPE,
        region: it,
        name  : "Bake in ${it}" as String
      ] as Map<String, Object>)
    }
  }

  @Override
  String parallelStageName() {
    return "Parallel Bake"
  }

  @Override
  Task completeParallel() {
    return new Task() {
      TaskResult execute(Stage stage) {
        new DefaultTaskResult(ExecutionStatus.SUCCEEDED, [:], [
            deploymentDetails: stage.execution.stages.findAll { it.type == MAYO_CONFIG_TYPE }.collect { it.context }
        ])
      }
    }
  }

  protected Date now() {
    return new Date()
  }
}
