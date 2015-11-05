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
import com.google.common.annotations.VisibleForTesting
import com.netflix.spinnaker.orca.clouddriver.pipeline.ScaleDownClusterStage
import com.netflix.spinnaker.orca.clouddriver.pipeline.DisableClusterStage
import com.netflix.spinnaker.orca.clouddriver.pipeline.ShrinkClusterStage
import com.netflix.spinnaker.orca.deprecation.DeprecationRegistry
import com.netflix.spinnaker.orca.clouddriver.pipeline.AbstractCloudProviderAwareStage
import com.netflix.spinnaker.orca.kato.pipeline.ModifyAsgLaunchConfigurationStage
import com.netflix.spinnaker.orca.kato.pipeline.RollingPushStage
import com.netflix.spinnaker.orca.kato.pipeline.support.SourceResolver
import com.netflix.spinnaker.orca.kato.pipeline.support.StageData
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.transform.Immutable
import org.springframework.batch.core.Step
import org.springframework.beans.factory.annotation.Autowired

@CompileStatic
@Deprecated
abstract class DeployStrategyStage extends AbstractCloudProviderAwareStage {

  @Autowired SourceResolver sourceResolver
  @Autowired ModifyAsgLaunchConfigurationStage modifyAsgLaunchConfigurationStage
  @Autowired RollingPushStage rollingPushStage
  @Autowired DeprecationRegistry deprecationRegistry

  @Autowired ShrinkClusterStage shrinkClusterStage
  @Autowired ScaleDownClusterStage scaleDownClusterStage
  @Autowired DisableClusterStage disableClusterStage

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
    new CleanupConfig(stageData.account, stageData.cluster, stageData.region, stageData.cloudProvider)
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
    def strategy = strategy(stage)
    strategy.composeFlow(this, stage)

    List<Step> steps = [buildStep(stage, "determineSourceServerGroup", DetermineSourceServerGroupTask)]
    if (!strategy.replacesBasicSteps()) {
      steps.addAll((basicSteps(stage) ?: []) as List<Step>)
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

  @VisibleForTesting
  @CompileDynamic
  protected void composeRedBlackFlow(Stage stage) {
    def stageData = stage.mapTo(StageData)
    def cleanupConfig = determineClusterForCleanup(stage)

    Map baseContext = [
      regions: [cleanupConfig.region],
      cluster: cleanupConfig.cluster,
      credentials: cleanupConfig.account,
      cloudProvider: cleanupConfig.cloudProvider
    ]

    if (stageData?.maxRemainingAsgs && (stageData?.maxRemainingAsgs > 0)) {
      Map shrinkContext = baseContext + [
        shrinkToSize: stageData.maxRemainingAsgs,
        allowDeleteActive: false,
        retainLargerOverNewer: false
      ]
      injectAfter(stage, "shrinkCluster", shrinkClusterStage, shrinkContext)
    }

    if (stageData.scaleDown) {
      def scaleDown = baseContext + [
        allowScaleDownActive: true,
        remainingFullSizeServerGroups: 1,
        preferLargerOverNewer: false
      ]
      injectAfter(stage, "scaleDown", scaleDownClusterStage, scaleDown)
    }

    injectAfter(stage, "disableCluster", disableClusterStage, baseContext + [
      remainingEnabledServerGroups: 1,
      preferLargerOverNewer: false
    ])
  }

  protected void composeRollingPushFlow(Stage stage) {
    def source = sourceResolver.getSource(stage)

    def modifyCtx = stage.context + [
      region: source.region,
      regions: [source.region],
      asgName: source.asgName,
      'deploy.server.groups': [(source.region): [source.asgName]],
      useSourceCapacity: true,
      credentials: source.account,
      source: [
        asgName: source.asgName,
        account: source.account,
        region: source.region,
        useSourceCapacity: true
      ]
    ]

    injectAfter(stage, "modifyLaunchConfiguration", modifyAsgLaunchConfigurationStage, modifyCtx)

    def terminationConfig = stage.mapTo("/termination", TerminationConfig)
    if (terminationConfig.relaunchAllInstances || terminationConfig.totalRelaunches > 0) {
      injectAfter(stage, "rollingPush", rollingPushStage, modifyCtx)
    }
  }

  @CompileDynamic
  protected void composeHighlanderFlow(Stage stage) {
    def cleanupConfig = determineClusterForCleanup(stage)
    Map shrinkContext = [
      regions: [cleanupConfig.region],
      cluster: cleanupConfig.cluster,
      credentials: cleanupConfig.account,
      cloudProvider: cleanupConfig.cloudProvider,
      shrinkToSize: 1,
      allowDeleteActive: true,
      retainLargerOverNewer: false
    ]
    injectAfter(stage, "shrinkCluster", shrinkClusterStage, shrinkContext)
  }

  @Immutable
  static class CleanupConfig {
    String account
    String cluster
    String region
    String cloudProvider
  }

  @Immutable
  static class TerminationConfig {
    String order
    boolean relaunchAllInstances
    int concurrentRelaunches
    int totalRelaunches
  }
}
