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

import com.netflix.spinnaker.orca.mine.MineService
import com.netflix.spinnaker.orca.mine.pipeline.MonitorCanaryStage
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.PipelineStage
import retrofit.client.Response
import retrofit.mime.TypedString
import spock.lang.Specification
import spock.lang.Subject

class RegisterCanaryTaskSpec extends Specification {

  MineService mineService = Mock(MineService)
  @Subject
  RegisterCanaryTask task = new RegisterCanaryTask(mineService: mineService)

  def 'canary registration'() {
    setup:
    def pipeline = new Pipeline(application: 'foo')

    def stageContext = [:]
    def monitorCanaryStage = new PipelineStage(pipeline, MonitorCanaryStage.MAYO_CONFIG_TYPE, stageContext)
    stageContext.putAll([
      account     : 'test',
      owner       : [name: 'cfieber', email: 'cfieber@netflix.com'],
      watchers    : [],
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
      deployedClusterPairs: [[
              canaryStage: monitorCanaryStage.id,
              canary: [
                clusterName: 'foo--cfieber-canary',
                serverGroup: 'foo--cfieber-canary-v000',
                account: 'test',
                region: 'us-west-1',
                imageId: 'ami-12345',
                buildNumber: 100
              ],
              baseline: [
                clusterName: 'foo--cfieber-baseline',
                serverGroup: 'foo--cfieber-baseline-v000',
                account: 'test',
                region: 'us-west-1',
                imageId: 'ami-12344',
                buildNumber: 99
              ]
      ]]
    ])

    Map captured

    when:
    def result = task.execute(monitorCanaryStage)

    then:
    1 * mineService.registerCanary(_) >> { Map c ->
      captured = c
      new Response('http:/mine', 200, 'OK', [], new TypedString('canaryId'))
    }

    then:
    1 * mineService.getCanary("canaryId") >> {
      captured
    }
    result.stageOutputs.canary
    with(result.stageOutputs.canary) {
      canaryDeployments.size() == 1
      canaryDeployments[0].canaryCluster.name == 'foo--cfieber-canary'
      canaryDeployments[0].baselineCluster.name == 'foo--cfieber-baseline'
      canaryConfig.lifetimeHours == 1
      canaryConfig.combinedCanaryResultStrategy == 'LOWEST'
    }
  }
}
