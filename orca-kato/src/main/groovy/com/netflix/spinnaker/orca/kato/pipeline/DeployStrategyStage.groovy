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

package com.netflix.spinnaker.orca.kato.pipeline

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.transform.Immutable
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.common.annotations.VisibleForTesting
import com.netflix.spinnaker.orca.oort.OortService
import com.netflix.spinnaker.orca.pipeline.LinearStage
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.batch.core.Step
import org.springframework.beans.factory.annotation.Autowired

@CompileStatic
abstract class DeployStrategyStage extends LinearStage {

  @Autowired OortService oort
  @Autowired ObjectMapper mapper
  @Autowired ResizeAsgStage resizeAsgStage
  @Autowired DisableAsgStage disableAsgStage
  @Autowired DestroyAsgStage destroyAsgStage

  DeployStrategyStage(String name) {
    super(name)
  }

  /**
   * @return the steps for the stage excluding whatever cleanup steps will be
   * handled by the deployment strategy.
   */
  protected abstract List<Step> basicSteps()

  /**
   * @param stage the stage configuration.
   * @return the details of the cluster that you are deploying to.
   */
  protected abstract ClusterConfig determineClusterForCleanup(Stage stage)

  /**
   * @param stage the stage configuration.
   * @return the strategy parameter.
   */
  protected abstract String strategy(Stage stage)

  @CompileDynamic
  @Override
  protected List<Step> buildSteps(Stage stage) {
    def strategy = strategy(stage)

    List<Step> steps
    switch(strategy) {
      case "redblack":
        steps = redBlackSteps(stage)
        break
      case "highlander":
        steps = highlanderStages(stage)
        break
      default:
        steps = basicSteps()
    }
    steps.each {
      it.name = it.name?.replace(DisableAsgStage.MAYO_CONFIG_TYPE, type)
                       ?.replace(DestroyAsgStage.MAYO_CONFIG_TYPE, type)
    }
    steps
  }

  @VisibleForTesting
  protected List<Step> redBlackSteps(Stage stage) {
    def steps = basicSteps()

    def clusterConfig = determineClusterForCleanup(stage)
    def lastAsg = getLastAsg(clusterConfig.app, clusterConfig.account, clusterConfig.cluster, clusterConfig.region)
    if (lastAsg) {
      def disableInputs = [asgName: lastAsg.name, regions: [lastAsg.region], credentials: clusterConfig.account]
      stage.context."disableAsg" = disableInputs
      steps.addAll(disableAsgStage.buildSteps(stage))
      if (Boolean.parseBoolean((((Map)stage.context.cluster)?.scaleDown ?: false)?.toString())) {
        def resizeInputs = new HashMap(disableInputs)
        resizeInputs.put "capacity", [min: 0, max: 0, desired: 0]
        stage.context."resizeAsg" = resizeInputs
        steps.addAll(resizeAsgStage.buildSteps(stage))
      }
    }

    steps
  }

  @VisibleForTesting
  protected List<Step> highlanderStages(Stage stage) {
    def steps = basicSteps()

    def clusterConfig = determineClusterForCleanup(stage)
    def existingAsgs = getExistingAsgs(clusterConfig.app, clusterConfig.account, clusterConfig.cluster)
    if (existingAsgs) {
      def destroyAsgDescriptions = []
      for (asg in existingAsgs) {
        destroyAsgDescriptions << [asgName: asg.name, credentials: clusterConfig.account, regions: [asg.region]]
        steps.addAll(destroyAsgStage.buildSteps(stage))
      }
      stage.context."destroyAsgDescriptions" = destroyAsgDescriptions
    }

    steps
  }

  @CompileDynamic
  private Map getLastAsg(String app, String account, String cluster, String region) {
    getExistingAsgs(app, account, cluster).findAll { it.region == region }.sort { a, b -> b.name <=> a.name }?.getAt(0)
  }

  @CompileDynamic
  private List<Map> getExistingAsgs(String app, String account, String cluster) {
    try {
      def response = oort.getCluster(app, account, cluster)
      def json = response.body.in().text
      def map = mapper.readValue(json, Map)
      map.serverGroups
    } catch (e) {
      null
    }
  }

  @Immutable
  protected static class ClusterConfig {
    String account
    String app
    String cluster
    String region

    @CompileDynamic
    static ClusterConfig fromContext(Map context) {
      String account = context.account
      String app = context.cluster.application
      String stack = context.cluster.stack
      String region
      if (context.containsKey("availabilityZones")) {
        region = ((Map)context.availabilityZones).keySet().getAt(0)
      } else if (context.containsKey("cluster") && context.cluster.containsKey("availabilityZones")) {
        region = ((Map)context.cluster.availabilityZones).keySet().getAt(0)
      } else {
        throw new IllegalArgumentException("Cowardishly failing because couldn't ascertain the target region.")
      }
      String cluster = "$app${stack ? '-' + stack : ''}"

      new ClusterConfig(account, app, cluster, region)
    }
  }
}
