/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.strategies

import com.netflix.spinnaker.moniker.Moniker
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution
import com.netflix.spinnaker.orca.api.pipeline.graph.StageGraphBuilder
import com.netflix.spinnaker.orca.clouddriver.pipeline.AbstractCloudProviderAwareStage
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.Location
import com.netflix.spinnaker.orca.clouddriver.tasks.DetermineHealthProvidersTask
import com.netflix.spinnaker.orca.clouddriver.utils.TrafficGuard
import com.netflix.spinnaker.orca.kato.pipeline.strategy.DetermineSourceServerGroupTask
import com.netflix.spinnaker.orca.kato.pipeline.support.StageData
import com.netflix.spinnaker.orca.kato.tasks.DiffTask
import com.netflix.spinnaker.orca.pipeline.StageExecutionFactory
import com.netflix.spinnaker.orca.api.pipeline.graph.TaskNode
import com.netflix.spinnaker.orca.api.pipeline.SyntheticStageOwner
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import com.netflix.spinnaker.orca.kato.pipeline.strategy.CloudrunSourceServerGroupTask

import javax.annotation.Nonnull

import static com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.strategies.DeployStagePreProcessor.StageDefinition
import static com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.TargetServerGroup.Support.locationFromStageData

@Slf4j
abstract class AbstractDeployStrategyStage extends AbstractCloudProviderAwareStage {

  @Autowired
  List<Strategy> strategies

  @Autowired
  NoStrategy noStrategy

  @Autowired(required = false)
  List<DiffTask> diffTasks

  @Autowired(required = false)
  List<DeployStagePreProcessor> deployStagePreProcessors = []

  @Autowired
  TrafficGuard trafficGuard

  AbstractDeployStrategyStage(String name) {
    super(name)
  }

  /**
   * @return the steps for the stage excluding whatever cleanup steps will be
   * handled by the deployment strategy.
   */
  protected
  abstract List<TaskNode.TaskDefinition> basicTasks(StageExecution stage)

  @Override
  void taskGraph(@Nonnull StageExecution stage, @Nonnull TaskNode.Builder builder) {

    String cloudProvider = getCloudProvider(stage);
    if ("cloudrun".equals(cloudProvider)) {
      builder
          .withTask("cloudrunSourceServerGroup", CloudrunSourceServerGroupTask)
          .withTask("determineHealthProviders", DetermineHealthProvidersTask)
    } else {
      builder
          .withTask("determineSourceServerGroup", DetermineSourceServerGroupTask)
          .withTask("determineHealthProviders", DetermineHealthProvidersTask)
    }

    correctContext(stage)
    deployStagePreProcessors.findAll { it.supports(stage) }.each {
      it.additionalSteps(stage).each {
        builder.withTask(it.name, it.taskClass)
      }
    }

    Strategy strategy = getStrategy(stage)
    if (!strategy.replacesBasicSteps()) {
      (basicTasks(stage) ?: []).each {
        builder.withTask(it.name, it.implementingClass)
      }

      if (diffTasks) {
        diffTasks.each { DiffTask diffTask ->
          try {
            builder.withTask(getDiffTaskName(diffTask.class.simpleName), diffTask.class)
          } catch (Exception e) {
            log.error("Unable to build diff task (name: ${diffTask.class.simpleName}: executionId: ${stage.execution.id})", e)
          }
        }
      }
    }
  }

  private Strategy getStrategy(StageExecution stage) {
    return (Strategy) strategies.findResult(noStrategy, {
      it.name.equalsIgnoreCase(stage.context.strategy as String) ? it : null
    })

  }

  @Override
  void beforeStages(@Nonnull StageExecution parent, @Nonnull StageGraphBuilder graph) {
    correctContext(parent)
    Strategy strategy = getStrategy(parent)
    def preProcessors = deployStagePreProcessors.findAll { it.supports(parent) }
    def stageData = parent.mapTo(StageData)
    List<StageExecution> stages = new ArrayList<>()
    stages.addAll(strategy.composeBeforeStages(parent))

    preProcessors.each {
      def defaultContext = [
        credentials  : stageData.account,
        cloudProvider: stageData.cloudProvider
      ]
      it.beforeStageDefinitions(parent).each {
        stages << StageExecutionFactory.newStage(
          parent.execution,
          it.stageDefinitionBuilder.type,
          it.name,
          defaultContext + it.context,
          parent,
          SyntheticStageOwner.STAGE_BEFORE
        )
      }
    }

    stages.forEach({ graph.append(it) })
  }

  @Override
  void afterStages(@Nonnull StageExecution parent, @Nonnull StageGraphBuilder graph) {
    Strategy strategy = getStrategy(parent)
    def preProcessors = deployStagePreProcessors.findAll { it.supports(parent) }
    def stageData = parent.mapTo(StageData)
    List<StageExecution> stages = new ArrayList<>()

    stages.addAll(strategy.composeAfterStages(parent))

    preProcessors.each {
      def defaultContext = [
        credentials  : stageData.account,
        cloudProvider: stageData.cloudProvider
      ]
      it.afterStageDefinitions(parent).each {
        stages << StageExecutionFactory.newStage(
          parent.execution,
          it.stageDefinitionBuilder.type,
          it.name,
          defaultContext + it.context,
          parent,
          SyntheticStageOwner.STAGE_AFTER
        )
      }
    }

    stages.forEach({ graph.append(it) })
  }

  @Override
  void onFailureStages(@Nonnull StageExecution stage, @Nonnull StageGraphBuilder graph) {
    Strategy strategy = getStrategy(stage)
    // Strategy shouldn't ever be null during regular execution, but that's not the case for unit tests
    // Either way, defensive programming
    if (strategy != null) {
      strategy.composeOnFailureStages(stage).forEach({ graph.append(it) })
    }

    deployStagePreProcessors
      .findAll { it.supports(stage) }
      .collect { it.onFailureStageDefinitions(stage) }
      .flatten()
      .forEach { StageDefinition stageDefinition ->
        graph.add {
          it.type = stageDefinition.stageDefinitionBuilder.type
          it.name = stageDefinition.name
          it.context = stageDefinition.context
        }
      }
  }

  /**
   * This nasty method is here because of an unfortunate misstep in pipeline configuration that introduced a nested
   * "cluster" key, when in reality we want all of the parameters to be derived from the top level. To preserve
   * functionality (and not break any contracts), this method is employed to move the nested data back to the context's
   * top-level
   */
  private static void correctContext(StageExecution stage) {
    if (stage.context.containsKey("cluster")) {
      stage.context.putAll(stage.context.cluster as Map)
    }
    stage.context.remove("cluster")
  }

  static String getDiffTaskName(String className) {
    try {
      className = className[0].toLowerCase() + className.substring(1)
      className = className.replaceAll("Task", "")
    } catch (e) {
    }
    return className
  }

  static class CleanupConfig {
    String account
    String cluster
    Moniker moniker
    String cloudProvider
    Location location

    static CleanupConfig fromStage(StageExecution stage) {
      return fromStage(stage.mapTo(StageData))
    }

    static CleanupConfig fromStage(StageData stageData) {
      def loc = locationFromStageData(stageData)
      new CleanupConfig(
        account: stageData.account,
        cluster: stageData.cluster,
        moniker: stageData.moniker,
        cloudProvider: stageData.cloudProvider,
        location: loc
      )
    }

    static Map<String, Object> toContext(StageData stageData) {
      CleanupConfig cleanupConfig = fromStage(stageData)
      Map<String, Object> context = new HashMap<>()
      context.put(cleanupConfig.getLocation().singularType(), cleanupConfig.getLocation().getValue())
      context.put("cloudProvider", cleanupConfig.getCloudProvider())
      context.put("cluster", cleanupConfig.getCluster())
      context.put("credentials", cleanupConfig.getAccount())
      context.put("moniker", cleanupConfig.getMoniker())
      return context
    }
  }
}
