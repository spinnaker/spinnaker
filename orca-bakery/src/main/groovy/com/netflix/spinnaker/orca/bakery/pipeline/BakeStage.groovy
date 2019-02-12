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

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.bakery.tasks.CompletedBakeTask
import com.netflix.spinnaker.orca.bakery.tasks.CreateBakeTask
import com.netflix.spinnaker.orca.bakery.tasks.MonitorBakeTask
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder
import com.netflix.spinnaker.orca.pipeline.TaskNode
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.tasks.artifacts.BindProducedArtifactsTask
import com.netflix.spinnaker.orca.pipeline.util.RegionCollector
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import javax.annotation.Nonnull

import static com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner.STAGE_BEFORE

@Slf4j
@Component
@CompileStatic
class BakeStage implements StageDefinitionBuilder {

  public static final String PIPELINE_CONFIG_TYPE = "bake"

  @Autowired
  RegionCollector regionCollector

  @Override
  void taskGraph(Stage stage, TaskNode.Builder builder) {
    if (isTopLevelStage(stage)) {
      builder
        .withTask("completeParallel", CompleteParallelBakeTask)
    } else {
      builder
        .withTask("createBake", CreateBakeTask)
        .withTask("monitorBake", MonitorBakeTask)
        .withTask("completedBake", CompletedBakeTask)
        .withTask("bindProducedArtifacts", BindProducedArtifactsTask)
    }
  }

  @Override
  @Nonnull
  List<Stage> parallelStages(
    @Nonnull Stage stage
  ) {
    if (isTopLevelStage(stage)) {
      return parallelContexts(stage).collect { context ->
        newStage(stage.execution, type, "Bake in ${context.region}", context, stage, STAGE_BEFORE)
      }
    } else {
      return Collections.emptyList()
    }
  }

  private boolean isTopLevelStage(Stage stage) {
    stage.parentStageId == null
  }

  @CompileDynamic
  Collection<Map<String, Object>> parallelContexts(Stage stage) {
    Set<String> deployRegions = (stage.context.region ? [stage.context.region] : []) as Set<String>
    deployRegions.addAll(stage.context.regions as Set<String> ?: [])

    if (!deployRegions.contains("global")) {
      deployRegions.addAll(regionCollector.getRegionsFromChildStages(stage))
      // TODO(duftler): Also filter added canary regions once canary supports multiple platforms.
    }

    log.info("Preparing package `${stage.context.package}` for bake in ${deployRegions.join(", ")}")
    if (!stage.context.amiSuffix) {
      stage.context.amiSuffix = now().format("yyyyMMddHHmmss", TimeZone.getTimeZone("UTC"))
    }
    return deployRegions.collect {
      stage.context - ["regions": stage.context.regions] + ([
        type  : PIPELINE_CONFIG_TYPE,
        region: it,
        name  : "Bake in ${it}" as String
      ] as Map<String, Object>)
    }
  }

  @Component
  @CompileStatic
  static class CompleteParallelBakeTask implements Task {
    TaskResult execute(Stage stage) {
      def bakeInitializationStages = stage.execution.stages.findAll {
        it.parentStageId == stage.parentStageId && it.status == ExecutionStatus.RUNNING
      }

      def relatedBakeStages = stage.execution.stages.findAll {
        it.type == PIPELINE_CONFIG_TYPE && bakeInitializationStages*.id.contains(it.parentStageId)
      }

      def globalContext = [
        deploymentDetails: relatedBakeStages.findAll{it.context.ami || it.context.imageId}.collect { Stage bakeStage ->
          def deploymentDetails = [:]
          ["ami", "imageId", "amiSuffix", "baseLabel", "baseOs", "refId", "storeType", "vmType", "region", "package", "cloudProviderType", "cloudProvider"].each {
            if (bakeStage.context.containsKey(it)) {
              deploymentDetails.put(it, bakeStage.context.get(it))
            }
          }

          return deploymentDetails
        }
      ]
      new TaskResult(ExecutionStatus.SUCCEEDED, [:], globalContext)
    }
  }

  protected Date now() {
    return new Date()
  }
}
