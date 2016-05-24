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

import com.netflix.spinnaker.orca.clouddriver.pipeline.AbstractCloudProviderAwareStage
import com.netflix.spinnaker.orca.clouddriver.pipeline.providers.aws.ApplySourceServerGroupCapacityStage
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.Location
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.TargetServerGroup
import com.netflix.spinnaker.orca.clouddriver.pipeline.providers.aws.CaptureSourceServerGroupCapacityTask
import com.netflix.spinnaker.orca.kato.pipeline.strategy.DetermineSourceServerGroupTask
import com.netflix.spinnaker.orca.kato.pipeline.support.StageData
import com.netflix.spinnaker.orca.kato.tasks.DiffTask
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.transform.Immutable
import groovy.util.logging.Slf4j
import org.springframework.batch.core.Step
import org.springframework.beans.factory.annotation.Autowired

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

  AbstractDeployStrategyStage(String name) {
    super(name)
  }

  /**
   * @return the steps for the stage excluding whatever cleanup steps will be
   * handled by the deployment strategy.
   */
  protected abstract List<Step> basicSteps(Stage stage)

  @Override
  public List<Step> buildSteps(Stage stage) {
    correctContext(stage)
    Strategy strategy = (Strategy) strategies.findResult(noStrategy, {
      it.name.equalsIgnoreCase(stage.context.strategy) ? it : null
    })
    strategy.composeFlow(stage)

    // TODO(ttomsu): This is currently an AWS-only stage. I need to add and support the "useSourceCapacity" option.
    List<Step> steps = [buildStep(stage, "determineSourceServerGroup", DetermineSourceServerGroupTask)]

    def stageData = stage.mapTo(StageData)
    deployStagePreProcessors.findAll { it.supports(stage) }.each {
      def defaultContext = [
        credentials  : stageData.account,
        cloudProvider: stageData.cloudProvider
      ]
      it.beforeStageDefinitions().each {
        injectBefore(stage, it.name, it.stageBuilder, defaultContext + it.context)
      }
      it.afterStageDefinitions().each {
        injectAfter(stage, it.name, it.stageBuilder, defaultContext + it.context)
      }
      it.additionalSteps().each {
        steps << buildStep(stage, it.name, it.taskClass)
      }
    }

    if (!strategy.replacesBasicSteps()) {
      steps.addAll((basicSteps(stage) ?: []) as List<Step>)

      if (diffTasks) {
        diffTasks.each { DiffTask diffTask ->
          try {
            steps << buildStep(stage, getDiffTaskName(diffTask.class.simpleName), diffTask.class)
          } catch (Exception e) {
            log.error("Unable to build diff task (name: ${diffTask.class.simpleName}: executionId: ${stage.execution.id})", e)
          }
        }
      }
    }
    return steps
  }

  /**
   * This nasty method is here because of an unfortunate misstep in pipeline configuration that introduced a nested
   * "cluster" key, when in reality we want all of the parameters to be derived from the top level. To preserve
   * functionality (and not break any contracts), this method is employed to move the nested data back to the context's
   * top-level
   */
  private static void correctContext(Stage stage) {
    if (stage.context.containsKey("cluster")) {
      stage.context.putAll(stage.context.cluster as Map)
    }
    stage.context.remove("cluster")
  }

  static String getDiffTaskName(String className) {
    try {
      className = className[0].toLowerCase() + className.substring(1)
      className = className.replaceAll("Task", "")
    } catch (e) {}
    return className
  }

  @Immutable
  static class CleanupConfig {
    String account
    String cluster
    String cloudProvider
    Location location

    static CleanupConfig fromStage(Stage stage) {
      def stageData = stage.mapTo(StageData)
      def loc = TargetServerGroup.Support.locationFromStageData(stageData)
      new CleanupConfig(
          account: stageData.account,
          cluster: stageData.cluster,
          cloudProvider: stageData.cloudProvider,
          location: loc
      )
    }
  }
}
