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
import com.netflix.spinnaker.orca.pipeline.StepProvider
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.transform.CompileDynamic
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
class BakeStage extends ParallelStage implements StepProvider {

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
  @CompileDynamic
  List<Map<String, Object>> parallelContexts(Stage stage) {
    def deployRegions = stage.context.region ? [stage.context.region] as Set<String> : []
    deployRegions += stage.context.regions ?: []

    if (!deployRegions.contains("global")) {
      stage.execution.stages.findAll { it.type == "deploy" }.each {
        ((it.context?.clusters?.availabilityZones ?: []) + [
          it.context.availabilityZones as Map,
          it.context?.cluster?.availabilityZones as Map
        ]).findAll { it }.each {
          // check each permutation of cluster config for target availability zones
          deployRegions << it.keySet()[0]
        }
      }
    }

    log.info("Preparing package `${stage.context.package}` for bake in ${deployRegions.join(", ")}")
    if (!stage.context.amiSuffix) {
      stage.context.amiSuffix = now().format("yyyyMMddHHmm", TimeZone.getTimeZone("UTC"))
    }
    return deployRegions.collect {
      stage.context - ["regions": stage.context.regions] + ([
        type  : MAYO_CONFIG_TYPE,
        region: it,
        name  : "Bake in ${it}" as String
      ] as Map<String, Object>)
    }
  }

  @Override
  String parallelStageName(Stage stage, boolean hasParallelFlows) {
    return hasParallelFlows ? "Multi-region Bake" : stage.name
  }

  @Override
  Task completeParallel() {
    return new Task() {
      TaskResult execute(Stage stage) {
        def bakeInitializationStages = stage.execution.stages.findAll {
          it.parentStageId == stage.parentStageId && it.status == ExecutionStatus.RUNNING
        }

        def globalContext = [
          deploymentDetails: stage.execution.stages.findAll {
            it.type == MAYO_CONFIG_TYPE && bakeInitializationStages*.id.contains(it.parentStageId) && it.context.ami
          }.collect { Stage bakeStage ->
            def deploymentDetails = [:]
            ["ami", "amiSuffix", "baseLabel", "baseOs", "storeType", "vmType", "region", "package", "cloudProviderType"].each {
              if (bakeStage.context.containsKey(it)) {
                deploymentDetails.put(it, bakeStage.context.get(it))
              }
            }
            return deploymentDetails
          }
        ]
        new DefaultTaskResult(ExecutionStatus.SUCCEEDED, [:], globalContext)
      }
    }
  }

  protected Date now() {
    return new Date()
  }
}
