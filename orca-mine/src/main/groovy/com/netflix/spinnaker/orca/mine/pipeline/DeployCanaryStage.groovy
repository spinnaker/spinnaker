/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.orca.mine.pipeline

import com.netflix.frigga.NameBuilder
import com.netflix.frigga.ami.AppVersion
import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.oort.tasks.FindAmiFromClusterTask
import com.netflix.spinnaker.orca.pipeline.model.Orchestration
import com.netflix.spinnaker.orca.pipeline.model.OrchestrationStage
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.kato.pipeline.ParallelDeployStage
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class DeployCanaryStage extends ParallelDeployStage {

  public static final String MAYO_CONFIG_TYPE = "deployCanary"

  @Autowired FindAmiFromClusterTask findAmi

  DeployCanaryStage() {
    super(MAYO_CONFIG_TYPE)
  }

  @Override
  List<Map<String, Object>> parallelContexts(Stage stage) {
    List<Map> baselineAmis = findBaselineAmis(stage)
    Map defaultStageContext = stage.context
    List<Map> canaryDeployments = defaultStageContext.clusterPairs
    def toContext = this.&clusterContext.curry(stage, defaultStageContext)

    return canaryDeployments.collect { Map canaryDeployment ->
      def canary = canaryDeployment.canary
      canary.strategy = "highlander"

      def baseline = canaryDeployment.baseline
      baseline.strategy = "highlander"
      def baselineAmi = baselineAmis.find { it.region == baseline.availabilityZones.keySet()[0] }
      baseline.amiName = baselineAmi?.ami
      baseline.buildUrl = createBuildUrl(baselineAmi)

      [baseline, canary]
    }.flatten().collect(toContext)
  }

  List<Map> findBaselineAmis(Stage stage) {
    Set<String> regions = stage.context.clusterPairs.collect { it.canary.availabilityZones.keySet() + it.baseline.availabilityZones.keySet() }.flatten()
    def findAmiCtx = [application: stage.execution.application, account: stage.context.baseline.account, cluster: stage.context.baseline.cluster, regions: regions]
    Stage s = new OrchestrationStage(new Orchestration(), "findAmi", findAmiCtx)
    TaskResult result = findAmi.execute(s)
    return result.stageOutputs.amiDetails
  }

  String createBuildUrl(Map deploymentDetail) {
    def appVersion = AppVersion.parseName(deploymentDetail?.tags?.find { it.key == 'appversion' }?.value)
    def buildHost = deploymentDetail?.tags?.find { it.key == 'build_host' }?.value
    if (appVersion && buildHost) {
      return "${buildHost}job/$appVersion.buildJobName/$appVersion.buildNumber/"
    }
    return null
  }

  @Override
  Task completeParallel() {
    return new Task() {
      TaskResult execute(Stage stage) {
        def context = stage.context
        def allStages = stage.execution.stages
        def deployStages = allStages.findAll { it.parentStageId == stage.id && it.type == ParallelDeployStage.MAYO_CONFIG_TYPE }
        def deployedClusterPairs = []
        for (Map pair in context.clusterPairs) {
          def resultPair = [canaryStage: context.canaryStageId]
          pair.each { String type, Map cluster ->
            def deployStage = deployStages.find {
              it.context.account == cluster.account &&
                it.context.application == cluster.application &&
                it.context.stack == cluster.stack &&
                it.context.freeFormDetails == cluster.freeFormDetails &&
                it.context.availabilityZones.keySet()[0] == cluster.availabilityZones.keySet()[0]
            }
            def region = cluster.availabilityZones.keySet()[0]
            def nameBuilder = new NameBuilder() {
              @Override
              public String combineAppStackDetail(String appName, String stack, String detail) {
                return super.combineAppStackDetail(appName, stack, detail)
              }
            }
            if (!cluster.amiName) {
              def ami = deployStage.context.deploymentDetails.find { it.region == region }

              cluster.amiName = ami?.ami
              cluster.buildUrl = createBuildUrl(ami)
            }
            resultPair[type + "Cluster"] = [
              name: nameBuilder.combineAppStackDetail(cluster.application, cluster.stack, cluster.freeFormDetails),
              serverGroup: deployStage.context.'deploy.server.groups'[region].first(),
              type: 'aws',
              accountName: cluster.account,
              region: region,
              imageId: cluster.amiName,
              buildId: cluster.buildUrl
            ]
          }
          deployedClusterPairs << resultPair
        }

        Logger log = LoggerFactory.getLogger(DeployCanaryStage)
        log.info("Completed Canary Deploys")
        Map canary = stage.context.canary
        canary.canaryDeployments = deployedClusterPairs
        new DefaultTaskResult(ExecutionStatus.SUCCEEDED, [canary: canary], [canary: canary, deployedClusterPairs: deployedClusterPairs])
      }
    }
  }
}

