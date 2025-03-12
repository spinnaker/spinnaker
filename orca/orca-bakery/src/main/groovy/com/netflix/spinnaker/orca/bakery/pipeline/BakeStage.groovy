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

import com.google.common.base.Joiner
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import com.netflix.spinnaker.kork.exceptions.ConstraintViolationException
import com.netflix.spinnaker.orca.api.pipeline.Task
import com.netflix.spinnaker.orca.api.pipeline.TaskResult
import com.netflix.spinnaker.orca.api.pipeline.graph.StageDefinitionBuilder
import com.netflix.spinnaker.orca.api.pipeline.graph.StageGraphBuilder
import com.netflix.spinnaker.orca.api.pipeline.graph.TaskNode
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution
import com.netflix.spinnaker.orca.bakery.tasks.CompletedBakeTask
import com.netflix.spinnaker.orca.bakery.tasks.CreateBakeTask
import com.netflix.spinnaker.orca.bakery.tasks.MonitorBakeTask
import com.netflix.spinnaker.orca.pipeline.StageExecutionFactory
import com.netflix.spinnaker.orca.pipeline.tasks.ToggleablePauseTask
import com.netflix.spinnaker.orca.pipeline.tasks.artifacts.BindProducedArtifactsTask
import com.netflix.spinnaker.orca.pipeline.util.RegionCollector
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import javax.annotation.Nonnull
import java.time.Clock
import java.util.stream.Collectors

import static com.netflix.spinnaker.orca.api.pipeline.SyntheticStageOwner.STAGE_BEFORE
import static java.time.Clock.systemUTC
import static java.time.ZoneOffset.UTC

@Slf4j
@Component
@CompileStatic
class BakeStage implements StageDefinitionBuilder {

  public static final String PIPELINE_CONFIG_TYPE = "bake"
  public static final String BAKE_PAUSE_TOGGLE = "stages.bake-stage.pause"

  private RegionCollector regionCollector
  private Clock clock
  private DynamicConfigService dynamicConfigService

  @Autowired
  BakeStage(RegionCollector regionCollector, DynamicConfigService dynamicConfigService, Clock clock = systemUTC()) {
    this.regionCollector = regionCollector
    this.clock = clock
    this.dynamicConfigService = dynamicConfigService
  }

  @Override
  void taskGraph(@Nonnull StageExecution stage, @Nonnull TaskNode.Builder builder) {
    if (isTopLevelStage(stage)) {
      builder
        .withTask("completeParallel", CompleteParallelBakeTask)
    } else {
      if (dynamicConfigService.isEnabled(BAKE_PAUSE_TOGGLE, false)) {
        log.info("Baking is currently paused. Adding pause task to ${stage.name} stage.")
        stage.context.put("pauseToggleKey", BAKE_PAUSE_TOGGLE)
        builder.withTask("delayBake", ToggleablePauseTask)
      }

      builder
        .withTask("createBake", CreateBakeTask)
        .withTask("monitorBake", MonitorBakeTask)
        .withTask("completedBake", CompletedBakeTask)
        .withTask("bindProducedArtifacts", BindProducedArtifactsTask)
    }
  }

  @Override
  void beforeStages(@Nonnull StageExecution parent, @Nonnull StageGraphBuilder graph) {
    if (isTopLevelStage(parent)) {
      parallelContexts(parent)
        .collect({ context ->
          StageExecutionFactory.newStage(parent.execution, type, "Bake in ${context.region}", context, parent, STAGE_BEFORE)
        })
        .forEach({ StageExecution s -> graph.add(s) })
    }
  }

  private boolean isTopLevelStage(StageExecution stage) {
    stage.parentStageId == null
  }

  @CompileDynamic
  Collection<Map<String, Object>> parallelContexts(StageExecution stage) {
    Set<String> deployRegions = (stage.context.region ? [stage.context.region] : []) as Set<String>
    deployRegions.addAll(stage.context.regions as Set<String> ?: [])


    Boolean skipRegionDetection = Boolean.TRUE == stage.context.skipRegionDetection
    if (!deployRegions.contains("global") && !skipRegionDetection) {
      deployRegions.addAll(regionCollector.getRegionsFromChildStages(stage))
      // TODO(duftler): Also filter added canary regions once canary supports multiple platforms.
    }

    log.info("Preparing package `${stage.context.package}` for bake in ${deployRegions.join(", ")}")
    if (!stage.context.amiSuffix) {
      stage.context.amiSuffix = clock.instant().atZone(UTC).format("yyyyMMddHHmmss")
    }
    return deployRegions.collect {
      stage.context - ["regions": stage.context.regions, "skipRegionDetection": stage.context.skipRegionDetection] + ([
        type  : PIPELINE_CONFIG_TYPE,
        region: it,
        name  : "Bake in ${it}" as String
      ] as Map<String, Object>)
    }
  }

  @Component
  @CompileStatic
  static class CompleteParallelBakeTask implements Task {
    public static final List<String> DEPLOYMENT_DETAILS_CONTEXT_FIELDS = [
      "ami",
      "amiName",
      "imageId",
      "imageName",
      "amiSuffix",
      "baseLabel",
      "baseOs",
      "refId",
      "storeType",
      "vmType",
      "region",
      "package",
      "cloudProviderType",
      "cloudProvider",
      "baseAmiId"
    ]
    DynamicConfigService dynamicConfigService

    @Autowired
    CompleteParallelBakeTask(DynamicConfigService dynamicConfigService) {
      this.dynamicConfigService = dynamicConfigService
    }

    @Nonnull
    TaskResult execute(@Nonnull StageExecution stage) {
      List<StageExecution> relatedBakeStages = stage.execution.stages.findAll {
        it.type == PIPELINE_CONFIG_TYPE && stage.id == it.parentStageId
      }

      def globalContext = [
        deploymentDetails: relatedBakeStages.findAll{it.context.ami || it.context.imageId}.collect { StageExecution bakeStage ->
          def deploymentDetails = [:]
          DEPLOYMENT_DETAILS_CONTEXT_FIELDS.each {
            if (bakeStage.context.containsKey(it)) {
              deploymentDetails.put(it, bakeStage.context.get(it))
            }
          }

          return deploymentDetails
        }
      ]

      if (failOnImageNameMismatchEnabled()) {
        // find distinct image names in bake stages that are actually related to the stage passed into the task
        List<Object> distinctImageNames = relatedBakeStages
          .findAll { childStage -> childStage.parentStageId == stage.id && childStage.context.imageName }
          .stream()
          .map { childStage -> childStage.context.imageName }
          .distinct()
          .collect(Collectors.toList())

        if (distinctImageNames.size() > 1) {
          throw new ConstraintViolationException(
            "Image names found in different regions do not match: ${Joiner.on(", ").join(distinctImageNames)}. "
            + "Re-run the bake to protect against deployment failures.")
        }
      }

      TaskResult.builder(ExecutionStatus.SUCCEEDED).outputs(globalContext).build()
    }

    private boolean failOnImageNameMismatchEnabled() {
      try {
        return dynamicConfigService.isEnabled("stages.bake.failOnImageNameMismatch", false)
      } catch (Exception e) {
        log.error("Unable to retrieve config value for stages.bake.failOnImageNameMismatch. Assuming false.", e)
        return false
      }
    }
  }
}
