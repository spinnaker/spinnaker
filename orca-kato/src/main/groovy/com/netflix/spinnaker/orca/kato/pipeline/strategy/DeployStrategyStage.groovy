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
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.transform.Immutable
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.common.annotations.VisibleForTesting
import com.netflix.spinnaker.orca.oort.OortService
import com.netflix.spinnaker.orca.pipeline.LinearStage
import groovy.transform.PackageScope
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.batch.core.Step
import org.springframework.beans.factory.annotation.Autowired

@CompileStatic
abstract class DeployStrategyStage extends LinearStage {
  Logger logger = LoggerFactory.getLogger(DeployStrategyStage)

  @Autowired OortService oort
  @Autowired ObjectMapper mapper
  @Autowired ResizeAsgStage resizeAsgStage
  @Autowired DisableAsgStage disableAsgStage
  @Autowired DestroyAsgStage destroyAsgStage
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
  public List<Step> buildSteps(Stage stage) {
    correctContext(stage)
    strategy(stage).composeFlow(this, stage)

    def source = getSource(stage)
    if (source) {
      stage.getContext().put("source", [
        asgName: source.asgName,
        account: source.account,
        region : source.region
      ])
    }

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

  /**
   * Determine an appropriate source asg for the current deploy stage:
   * - If a 'source' is available in the stage context, use it
   * - Otherwise, lookup the latest ASG in the target account/region/cluster and use it
   */
  @VisibleForTesting
  @PackageScope
  StageData.Source getSource(Stage stage) {
    def stageData = stage.mapTo(StageData)
    if (stageData.source) {
      // has an existing source, return it
      return stageData.source
    }

    def existingAsgs = getExistingAsgs(
      stageData.application, stageData.account, stageData.cluster, stageData.providerType
    )

    if (!existingAsgs || !stageData.availabilityZones) {
      return null
    }

    def targetRegion = stageData.availabilityZones.keySet()[0]

    def sortedAsgNames = sortAsgs(existingAsgs.findAll { it.region == targetRegion }.collect { it.name })
    def latestAsgName = sortedAsgNames ? sortedAsgNames.last() : null
    def latestAsg = latestAsgName ? existingAsgs.find { it.name == latestAsgName && it.region == targetRegion } : null

    return latestAsg ? new StageData.Source(
      account: stageData.account, region: latestAsg["region"] as String, asgName: latestAsg["name"] as String
    ) : null
  }

  @VisibleForTesting
  @CompileDynamic
  protected void composeRedBlackFlow(Stage stage) {
    def stageData = stage.mapTo(StageData)
    def cleanupConfig = determineClusterForCleanup(stage)
    def existingAsgs = getExistingAsgs(
      stageData.application, cleanupConfig.account, cleanupConfig.cluster, stageData.providerType
    )

    if (existingAsgs) {
      for (entry in stageData.availabilityZones) {
        def region = entry.key
        if (!cleanupConfig.regions.contains(region)) {
          continue
        }
        def asgs = sortAsgs(existingAsgs.findAll { it.region == region }.collect { it.name })
        def latestAsg = asgs.size() > 0 ? asgs?.last() : null

        if (!latestAsg) {
          continue
        }
        def nextStageContext = [asgName: latestAsg, regions: [region], credentials: cleanupConfig.account]

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
        // delete the oldest asgs until there are maxRemainingAsgs left (including the newly created one)
        if (stageData?.maxRemainingAsgs > 0 && (asgs.size() - stageData.maxRemainingAsgs) >= 0) {
          asgs[0..(asgs.size() - stageData.maxRemainingAsgs)].each { asg ->
            logger.info("Injecting destroyAsg stage (${region}:${asg})")
            nextStageContext.putAll([asgName: asg, credentials: cleanupConfig.account, regions: [region]])
            injectAfter(stage, "destroyAsg", destroyAsgStage, nextStageContext)
          }
        }
      }
    }
  }

  @CompileDynamic
  protected void composeHighlanderFlow(Stage stage) {
    def stageData = stage.mapTo(StageData)
    def cleanupConfig = determineClusterForCleanup(stage)
    def existingAsgs = getExistingAsgs(
      stageData.application, cleanupConfig.account, cleanupConfig.cluster, stageData.providerType
    )
    if (existingAsgs) {
      for (entry in stageData.availabilityZones) {
        def region = entry.key
        if (!cleanupConfig.regions.contains(region)) {
          continue
        }

        existingAsgs.findAll { it.region == region }.each { Map asg ->
          def nextContext = [asgName: asg.name, credentials: cleanupConfig.account, regions: [region]]
          if (nextContext.asgName) {
            def names = Names.parseName(nextContext.asgName as String)
            if (stageData.application != names.app) {
              logger.info("Next stage context targeting application not belonging to the source stage! ${mapper.writeValueAsString(nextContext)}")
              return
            }
          }

          logger.info("Injecting destroyAsg stage (${asg.region}:${asg.name})")
          injectAfter(stage, "destroyAsg", destroyAsgStage, nextContext)
        }
      }
    }
  }

  protected List sortAsgs(List asgs) {
    def mc = [
        compare: {
          String a, String b ->
            // cases where there is no version
            if(a.lastIndexOf("-v") == -1 && Integer.parseInt(b.substring(b.lastIndexOf("-v") + 2)) > 900) {
              1
            } else if(a.lastIndexOf("-v") == -1 && Integer.parseInt(b.substring(b.lastIndexOf("-v") + 2)) < 900) {
              -1
            } else if(b.lastIndexOf("-v") == -1 && Integer.parseInt(a.substring(a.lastIndexOf("-v") + 2)) < 900) {
              1
            } else if(b.lastIndexOf("-v") == -1 && Integer.parseInt(a.substring(a.lastIndexOf("-v") + 2)) > 900) {
              -1
              // cases where versions cross 999
            } else if(Integer.parseInt(a.substring(a.lastIndexOf("-v") + 2)) < 900 && Integer.parseInt(b.substring(b.lastIndexOf("-v") + 2)) > 900) {
              1
            } else if(Integer.parseInt(a.substring(a.lastIndexOf("-v") + 2)) > 900 && Integer.parseInt(b.substring(b.lastIndexOf("-v") + 2)) < 900) {
              -1
            } else { // normal case
              int aNum = Integer.parseInt(a.substring(a.lastIndexOf("-v") + 2))
              int bNum = Integer.parseInt(b.substring(b.lastIndexOf("-v") + 2))
              aNum.equals(bNum) ? 0 : Math.abs(aNum) < Math.abs(bNum) ? -1 : 1
            }
        }
      ] as Comparator
    return asgs.sort(true, mc)
  }

  @VisibleForTesting
  @PackageScope
  List<Map> getExistingAsgs(String app, String account, String cluster, String providerType) {
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
    String credentials
    String freeFormDetails
    String application
    String stack
    String providerType = "aws"
    boolean scaleDown
    Map<String, List<String>> availabilityZones
    int maxRemainingAsgs

    Source source

    String getCluster() {
      def builder = new AutoScalingGroupNameBuilder()
      builder.appName = application
      builder.stack = stack
      builder.detail = freeFormDetails

      return builder.buildGroupName()
    }

    String getAccount() {
      if (account && credentials && account != credentials) {
        throw new IllegalStateException("Cannot specify different values for 'account' and 'credentials' (${application})")
      }
      return account ?: credentials
    }

    static class Source {
      String account
      String region
      String asgName
    }
  }

  @Immutable
  static class CleanupConfig {
    String account
    String cluster
    List<String> regions
  }
}
