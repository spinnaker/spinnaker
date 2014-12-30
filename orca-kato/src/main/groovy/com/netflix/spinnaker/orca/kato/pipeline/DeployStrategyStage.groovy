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

import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.transform.Immutable
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.common.annotations.VisibleForTesting
import com.netflix.spinnaker.orca.oort.OortService
import com.netflix.spinnaker.orca.pipeline.LinearStage
import org.springframework.batch.core.Step
import org.springframework.beans.factory.annotation.Autowired

@CompileStatic
abstract class DeployStrategyStage extends LinearStage {
  static final String RED_BLACK_KEY = "redblack"
  static final String HIGHLANDER_KEY = "highlander"
  static final String CLUSTER_KEY = "cluster"
  static final String SCALE_DOWN_KEY = "scaleDown"
  static final String AVAILABILITY_ZONES_KEY = "availabilityZones"

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
      case RED_BLACK_KEY:
        steps = redBlackSteps(stage)
        break
      case HIGHLANDER_KEY:
        steps = highlanderStages(stage)
        break
      default:
        steps = basicSteps()
    }
    steps.each {
      if (it.name) {
        it.name = it.name
            .replaceFirst(DisableAsgStage.MAYO_CONFIG_TYPE, type)
            .replaceFirst(DestroyAsgStage.MAYO_CONFIG_TYPE, type)
            .replaceFirst(ResizeAsgStage.MAYO_CONFIG_TYPE, type)
      }
    }
    steps
  }

  @VisibleForTesting
  protected List<Step> redBlackSteps(Stage stage) {
    def steps = basicSteps()
    def stageProps = new DecomposedStageProperties(stage)

    applyScaleDownPolicy(stageProps, steps)
    applyDisablePolicy(stageProps, steps)

    steps
  }

  private void applyDisablePolicy(DecomposedStageProperties stageProperties, List<Step> steps) {
    stageProperties.stage.context.put(DisableAsgStage.MAYO_CONFIG_TYPE, stageProperties.disableInputs)
    steps.addAll(disableAsgStage.buildSteps(stageProperties.stage))
  }

  private void applyScaleDownPolicy(DecomposedStageProperties stageProperties, List<Step> steps) {
    if (stageProperties.isScaleDown()) {
      def resizeInputs = new HashMap(stageProperties.disableInputs)
      resizeInputs.put(ResizeAsgStage.CAPACITY_KEY, [min: 0, max:0, desired: 0])
      stageProperties.stage.context.put(ResizeAsgStage.MAYO_CONFIG_TYPE, resizeInputs)
      steps.addAll(resizeAsgStage.buildSteps(stageProperties.stage))
    }
  }

  class DecomposedStageProperties {
    final Stage stage
    final Map context
    final Map cluster
    final ClusterConfig clusterConfig
    boolean scaleDown

    DecomposedStageProperties(Stage stage) {
      this.stage = stage
      this.context = stage.context
      if (context.containsKey(CLUSTER_KEY)) {
        this.cluster = context[CLUSTER_KEY] as Map
      } else {
        this.cluster = [:]
      }
      this.clusterConfig = determineClusterForCleanup(stage)
    }

    Boolean isScaleDown() {
      if (!this.scaleDown) {
        def srcMap = cluster.containsKey(SCALE_DOWN_KEY) ? cluster : context
        this.scaleDown = Boolean.parseBoolean(srcMap[SCALE_DOWN_KEY].toString())
      }
      this.scaleDown
    }

    Map getDisableInputs() {
      def latestAsg = getLatestAsg()
      latestAsg ? [asgName: latestAsg.name, regions: [latestAsg.region], credentials: clusterConfig.account] : [:]
    }

    @CompileDynamic
    Map getLatestAsg() {
      DeployStrategyStage.this.getExistingAsgs(clusterConfig.app, clusterConfig.account, clusterConfig.cluster, clusterConfig.providerType)
        .findAll { it.region == clusterConfig.region }
        .sort { a, b -> b.name <=> a.name }?.getAt(0)
    }
  }

  @VisibleForTesting
  protected List<Step> highlanderStages(Stage stage) {
    def steps = basicSteps()

    def clusterConfig = determineClusterForCleanup(stage)
    def existingAsgs = getExistingAsgs(clusterConfig.app, clusterConfig.account, clusterConfig.cluster, clusterConfig.providerType)
    if (existingAsgs) {
      def destroyAsgDescriptions = []
      for (asg in existingAsgs) {
        destroyAsgDescriptions << [asgName: asg.name, credentials: clusterConfig.account, regions: [asg.region]]
        steps.addAll(destroyAsgStage.buildSteps(stage))
      }
      stage.context[DestroyAsgStage.DESTROY_ASG_DESCRIPTIONS_KEY] = destroyAsgDescriptions
    }

    steps
  }

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

  @Immutable
  protected static class ClusterConfig {
    String account
    String app
    String cluster
    String region
    String providerType

    @CompileDynamic
    static ClusterConfig fromContext(Map context) {
      String account = context.account
      String app = fromClusterOrContext(context, "application")
      String stack = fromClusterOrContext(context, "stack")
      String region
      if (context.containsKey(AVAILABILITY_ZONES_KEY)) {
        region = ((Map)context[AVAILABILITY_ZONES_KEY]).keySet().getAt(0)
      } else if (context.containsKey(CLUSTER_KEY) && context[CLUSTER_KEY].containsKey(AVAILABILITY_ZONES_KEY)) {
        region = ((Map)context[CLUSTER_KEY][AVAILABILITY_ZONES_KEY]).keySet().getAt(0)
      } else {
        throw new IllegalArgumentException("Cowardishly failing because couldn't ascertain the target region.")
      }
      String cluster = "$app${stack ? '-' + stack : ''}"
      String providerType = context.providerType ?: "aws"

      new ClusterConfig(account, app, cluster, region, providerType)
    }

    static def fromClusterOrContext(Map context, String property) {
      context.containsKey(CLUSTER_KEY) ? context[CLUSTER_KEY][property] : context[property]
    }
  }
}
