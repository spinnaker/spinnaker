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

import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.mine.tasks.CleanupCanaryTask
import com.netflix.spinnaker.orca.mine.tasks.MonitorCanaryTask
import com.netflix.spinnaker.orca.mine.tasks.RegisterCanaryTask
import com.netflix.spinnaker.orca.oort.tasks.FindAmiFromClusterTask
import com.netflix.spinnaker.orca.pipeline.model.Orchestration
import com.netflix.spinnaker.orca.pipeline.model.OrchestrationStage
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.kato.pipeline.ParallelDeployStage
import org.springframework.batch.core.job.builder.FlowBuilder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class CanaryStage extends ParallelDeployStage {

  public static final String MAYO_CONFIG_TYPE = "canary"

  @Autowired FindAmiFromClusterTask findAmi

  CanaryStage() {
    super(MAYO_CONFIG_TYPE)
  }

  @Override
  List<Map<String, Object>> parallelContexts(Stage stage) {

    List<Map> baselineAmis = findBaselineAmis(stage)
    Map defaultStageContext = stage.context
    List<Map> canaries = defaultStageContext.remove('canaries')
    def toContext = this.&clusterContext.curry(stage, defaultStageContext)

    return canaries.collect { Map cluster ->
      def baseline = new LinkedHashMap(cluster)
      def canary = new LinkedHashMap(cluster)
      def detailsPrefix = cluster.freeFormDetails ? "${cluster.freeFormDetails}_" : ""

      baseline.amiName = baselineAmis.find { it.region == baseline.availabilityZones.keySet()[0] }?.name
      baseline.freeFormDetails = "${detailsPrefix}baseline".toString()
      canary.freeFormDetails = "${detailsPrefix}canary".toString()
      [baseline, canary]
    }.flatten().collect(toContext)
  }

  List<Map> findBaselineAmis(Stage stage) {
    Set<String> regions = stage.context.canaries.collect { it.availabilityZones.keySet() }.flatten()
    def findAmiCtx = [application: stage.execution.application, account: stage.context.baseline.account, cluster: stage.context.baseline.cluster, regions: regions]
    Stage s = new OrchestrationStage(new Orchestration(), "findAmi", findAmiCtx)
    TaskResult result = findAmi.execute(s)
    return result.stageOutputs.amiDetails
  }

  @Override
  FlowBuilder buildInternal(FlowBuilder jobBuilder, Stage stage) {
    super.buildInternal(jobBuilder, stage)
      .next(buildStep(stage, "registerCanary", RegisterCanaryTask))
      .next(buildStep(stage, "monitorCanary", MonitorCanaryTask))
      .next(buildStep(stage, "cleanupCanary", CleanupCanaryTask))
  }
}

