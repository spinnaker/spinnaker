/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

import com.netflix.frigga.Names
import com.netflix.frigga.autoscaling.AutoScalingGroupNameBuilder
import com.netflix.spinnaker.orca.kato.pipeline.DestroyAsgStage
import com.netflix.spinnaker.orca.kato.pipeline.DisableAsgStage
import com.netflix.spinnaker.orca.kato.pipeline.ModifyScalingProcessStage
import com.netflix.spinnaker.orca.kato.pipeline.ResizeAsgStage
import com.netflix.spinnaker.orca.kato.pipeline.ShrinkClusterStage
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.transform.Immutable
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.common.annotations.VisibleForTesting
import com.netflix.spinnaker.orca.oort.OortService
import com.netflix.spinnaker.orca.pipeline.LinearStage
import java.util.logging.Logger
import org.springframework.batch.core.Step
import org.springframework.beans.factory.annotation.Autowired

@CompileStatic
abstract class DeployStrategyStage extends LinearStage {
  Logger logger = Logger.getLogger(DeployStrategyStage.simpleName)

  @Autowired OortService oort
  @Autowired ObjectMapper mapper
  @Autowired ResizeAsgStage resizeAsgStage
  @Autowired DisableAsgStage disableAsgStage
  @Autowired DestroyAsgStage destroyAsgStage
  @Autowired ShrinkClusterStage shrinkClusterStage
  @Autowired ModifyScalingProcessStage modifyScalingProcessStage

  DeployStrategyStage(String name) {
    super(name)
  }

  /**
   * @return the steps for the stage excluding whatever cleanup steps will be
   * handled by the deployment strategy.
   */
  protected abstract List<Step> basicSteps(Stage stage)

  /**
   * @param stage the stage configuration.
   * @return the details of the cluster that you are deploying to.
   */
  protected CleanupConfig determineClusterForCleanup(Stage stage) {
    def stageData = stage.mapTo(StageData)
    new CleanupConfig(stageData.account, stageData.cluster, stageData.availabilityZones.keySet().toList())
  }

  /**
   * @param stage the stage configuration.
   * @return the strategy parameter.
   */
  protected Strategy strategy(Stage stage) {
    def stageData = stage.mapTo(StageData)
    Strategy.fromStrategy(stageData.strategy)
  }

  @Override
  protected List<Step> buildSteps(Stage stage) {
    correctContext(stage)
    strategy(stage).composeFlow(this, stage)
    basicSteps(stage)
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

  @VisibleForTesting
  @CompileDynamic
  protected void composeRedBlackFlow(Stage stage) {
    def stageData = stage.mapTo(StageData)
    def cleanupConfig = determineClusterForCleanup(stage)
    def existingAsgs = getExistingAsgs(stageData.application, cleanupConfig.account,
                                       cleanupConfig.cluster, stageData.providerType)

    if (existingAsgs) {
      for (entry in stageData.availabilityZones) {
        def region = entry.key
        if (!cleanupConfig.regions.contains(region)) {
          continue
        }
        def latestAsg = existingAsgs.findAll { it.region == region }.sort { a, b -> b.name <=> a.name }?.getAt(0)
        def nextStageContext = [asgName: latestAsg.name, regions: [region], credentials: cleanupConfig.account]

        if (nextStageContext.asgName) {
          def names = Names.parseName(nextStageContext.asgName as String)
          if (stageData.application != names.app) {
            logger.info("Next stage context targeting application not belonging to the source stage! ${mapper.writeValueAsString(nextStageContext)}")
            continue
          }
        }

        if (stageData.scaleDown) {
          nextStageContext.capacity = [min: 0, max: 0, desired: 0]
          injectAfter(stage, "scaleDown", resizeAsgStage, nextStageContext)
        }
        injectAfter(stage, "disable", disableAsgStage, nextStageContext)
        if (stageData.scaleDown) {
          nextStageContext.putAll([action: "resume", processes: ["Terminate"]])
          injectAfter(stage, "enableTerminate", modifyScalingProcessStage, nextStageContext)
        }
        if (stageData.shrinkCluster) {
          Names names = Names.parseName(latestAsg.name as String)
          def shrinkContext = [application: names.app, clusterName: names.cluster,
                               forceDelete: true, regions: [region], credentials: cleanupConfig.account]
          injectAfter(stage, "shrinkCluster", shrinkClusterStage, shrinkContext)
        }
      }
    }
  }

  @CompileDynamic
  protected void composeHighlanderFlow(Stage stage) {
    def stageData = stage.mapTo(StageData)
    def cleanupConfig = determineClusterForCleanup(stage)
    def existingAsgs = getExistingAsgs(stageData.application, cleanupConfig.account,
                                       cleanupConfig.cluster, stageData.providerType)
    if (existingAsgs) {
      for (entry in stageData.availabilityZones) {
        def region = entry.key
        if (!cleanupConfig.regions.contains(region)) {
          continue
        }
        for (asg in existingAsgs) {
          def nextContext = [asgName: asg.name, credentials: cleanupConfig.account, regions: [region]]
          if (nextContext.asgName) {
            def names = Names.parseName(nextContext.asgName as String)
            if (stageData.application != names.app) {
              logger.info("Next stage context targeting application not belonging to the source stage! ${mapper.writeValueAsString(nextContext)}")
              continue
            }
          }

          injectAfter(stage, "destroyAsg", destroyAsgStage, nextContext)
        }
      }
    }
  }

  private List<Map> getExistingAsgs(String app, String account, String cluster, String providerType) {
    try {
      def response = oort.getCluster(app, account, cluster, providerType)
      def json = response.body.in().text
      def map = mapper.readValue(json, Map)
      map.serverGroups as List<Map>
    } catch (e) {
      null
    }
  }

  static class StageData {
    String strategy
    String account
    String freeFormDetails
    String application
    String stack
    String providerType = "aws"
    boolean scaleDown
    boolean shrinkCluster
    Map<String, List<String>> availabilityZones

    String getCluster() {
      def builder = new AutoScalingGroupNameBuilder()
      builder.appName = application
      builder.stack = stack
      builder.detail = freeFormDetails

      return builder.buildGroupName()
    }
  }

  @Immutable
  static class CleanupConfig {
    String account
    String cluster
    List<String> regions
  }
}
