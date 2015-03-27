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
import com.netflix.spinnaker.orca.mine.MineService
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.PipelineStage
import spock.lang.Specification
import spock.lang.Subject

class RegisterCanaryTaskSpec extends Specification {

  MineService mineService = Mock(MineService)
  @Subject RegisterCanaryTask task = new RegisterCanaryTask(mineService: mineService)

  def 'canary registration'() {
    setup:
    def stage = new PipelineStage(new Pipeline(application: "foo"), "canary", [
      owner: [name: 'cfieber', email: 'cfieber@netflix.com'],
      canaries: [[credentials: 'test', region: 'us-east-1', application: 'foo']],
      canaryConfig: [
        lifetimeHours: 1,
        combinedCanaryResultStrategy: 'LOWEST',
        canarySuccessCriteria: [canaryResultScore: 95],
        canaryHealthCheckHandler: [minimumCanaryResultScore: 75],
        canaryAnalysisConfig: [
          name: 'beans',
          beginCanaryAnalysisAfterMins: 5,
          notificationHours: [1, 2],
          canaryAnalysisIntervalMins: 15
        ]
      ]
    ])

    when:
    def result = task.execute(stage)

    then:
    1 * mineService.registerCanary('foo', _) >> { app, canary ->
      new ObjectMapper().convertValue(canary, Map)
    }
    result.stageOutputs.canary
    result.stageOutputs.canary.canaryDeployments.size() == 1
    with(result.stageOutputs.canary.canaryDeployments[0]) {
      canaryCluster.name == 'foo--canary'
      baselineCluster.name == 'foo--baseline'
    }
    with (result.stageOutputs.canary.canaryConfig) {
      lifetimeHours == 1
      combinedCanaryResultStrategy == 'LOWEST'
    }
  }
}
