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

package com.netflix.spinnaker.orca.mine.tasks

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.kato.pipeline.ParallelDeployStage
import com.netflix.spinnaker.orca.mine.Canary
import com.netflix.spinnaker.orca.mine.MineService
import com.netflix.spinnaker.orca.mine.pipeline.CanaryStage
import com.netflix.spinnaker.orca.mine.pipeline.MonitorCanaryStage
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.PipelineStage
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject

class RegisterCanaryTaskSpec extends Specification {

  MineService mineService = Mock(MineService)
  @Shared ObjectMapper objectMapper = new ObjectMapper()
  @Shared ResultSerializationHelper resultSerializationHelper = new ResultSerializationHelper(objectMapper: objectMapper)
  @Subject
  RegisterCanaryTask task = new RegisterCanaryTask(mineService: mineService, objectMapper: objectMapper, resultSerializationHelper: resultSerializationHelper)

  def 'canary registration'() {
    setup:
    def pipeline = new Pipeline(application: 'foo')

    def context = [
      account     : 'test',
      owner       : [name: 'cfieber', email: 'cfieber@netflix.com'],
      watchers    : [],
      canaries    : [[credentials: 'test', availabilityZones: ['us-east-1': ['us-east-1c', 'us-east-1d', 'us-east-1e']], application: 'foo']],
      canaryConfig: [
        lifetimeHours           : 1,
        combinedCanaryResultStrategy: 'LOWEST',
        canarySuccessCriteria   : [canaryResultScore: 95],
        canaryHealthCheckHandler: [minimumCanaryResultScore: 75],
        canaryAnalysisConfig    : [
          name                      : 'beans',
          beginCanaryAnalysisAfterMins: 5,
          notificationHours         : [1, 2],
          canaryAnalysisIntervalMins: 15
        ]
      ],
    ]

    def monitorCanaryStage = new PipelineStage(pipeline, MonitorCanaryStage.MAYO_CONFIG_TYPE, context)
    def deployCanariesStage = new PipelineStage(pipeline, CanaryStage.MAYO_CONFIG_TYPE, context)
    Map<String, Object> baselineContext = context + ['deploy.server.groups': ['us-east-1': ['foo--baseline-v000']]]
    def deployBaselineStage = new PipelineStage(pipeline, ParallelDeployStage.MAYO_CONFIG_TYPE, baselineContext)
    deployBaselineStage.parentStageId = deployCanariesStage.id
    Map<String, Object> canaryContext = context + ['deploy.server.groups': ['us-east-1': ['foo--canary-v000']]]
    def deployCanaryStage = new PipelineStage(pipeline, ParallelDeployStage.MAYO_CONFIG_TYPE, canaryContext)
    deployCanaryStage.parentStageId = deployCanariesStage.id
    pipeline.stages.addAll([deployCanariesStage, deployBaselineStage, deployCanaryStage, monitorCanaryStage])
    Canary captured

    when:
    def result = task.execute(monitorCanaryStage)

    then:
    1 * mineService.registerCanary(_) >> { Canary c ->
      captured = c
      "canaryId"
    }

    then:
    1 * mineService.checkCanaryStatus("canaryId") >> {
      captured
    }
    result.stageOutputs.canary
    with(result.stageOutputs.canary) {
      canaryDeployments.size() == 1
      canaryDeployments[0].canaryCluster.name == 'foo--canary-v000'
      canaryDeployments[0].baselineCluster.name == 'foo--baseline-v000'
      canaryConfig.lifetimeHours == 1
      canaryConfig.combinedCanaryResultStrategy == 'LOWEST'
    }
  }

  def 'type cluster creation'() {
    when:
    def typedCluster = RegisterCanaryTask.buildCluster(asg, region, account)

    then:
    (typedCluster == null) == isNull
    if (!isNull) {
      typedCluster.type == type
      typedCluster.cluster.accountName == account
      typedCluster.cluster.region == region
      typedCluster.cluster.name == asg
    }

    where:
    asg                  | region      | isNull | type       | account
    'foo--canary-v000'   | 'us-east-1' | false  | 'canary'   | 'test'
    'foo--baseline-v000' | 'us-west-1' | false  | 'baseline' | 'prod'

  }

}
