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
import com.netflix.spinnaker.orca.mine.pipeline.DeployCanaryStage
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.Stage
import retrofit.client.Response
import retrofit.mime.TypedString
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class RegisterCanaryTaskSpec extends Specification {

  MineService mineService = Mock(MineService)
  @Subject
  RegisterCanaryTask task = new RegisterCanaryTask(mineService: mineService)

  def 'canary registration'() {
    setup:
    def pipeline = new Pipeline(application: 'foo')

    def canaryStageId = UUID.randomUUID().toString()
    def parentStageId = UUID.randomUUID().toString()
    def deployCanaryStage = new Stage<>(pipeline, DeployCanaryStage.PIPELINE_CONFIG_TYPE, [
      canaryStageId       : canaryStageId,
      account             : 'test',
      canary              : [
        owner            : [name: 'cfieber', email: 'cfieber@netflix.com'],
        watchers         : [],
        canaryConfig     : [
          lifetimeHours               : 1,
          combinedCanaryResultStrategy: 'LOWEST',
          canarySuccessCriteria       : [canaryResultScore: 95],
          canaryHealthCheckHandler    : [minimumCanaryResultScore: 75],
          canaryAnalysisConfig        : [
            name                        : 'beans',
            beginCanaryAnalysisAfterMins: 5,
            notificationHours           : [1, 2],
            canaryAnalysisIntervalMins  : 15
          ]
        ],
        canaryDeployments: [[
                              canaryCluster  : [
                                name       : 'foo--cfieber-canary',
                                serverGroup: 'foo--cfieber-canary-v000',
                                accountName: 'test',
                                region     : 'us-west-1',
                                imageId    : 'ami-12345',
                                buildId    : 100
                              ],
                              baselineCluster: [
                                name       : 'foo--cfieber-baseline',
                                serverGroup: 'foo--cfieber-baseline-v000',
                                accountName: 'test',
                                region     : 'us-west-1',
                                imageId    : 'ami-12344',
                                buildId    : 99
                              ]
                            ]]
      ],
      deployedClusterPairs: [[
                               canaryStage    : canaryStageId,
                               canaryCluster  : [
                                 name       : 'foo--cfieber-canary',
                                 serverGroup: 'foo--cfieber-canary-v000',
                                 accountName: 'test',
                                 region     : 'us-west-1',
                                 imageId    : 'ami-12345',
                                 buildId    : 100
                               ],
                               baselineCluster: [
                                 name       : 'foo--cfieber-baseline',
                                 serverGroup: 'foo--cfieber-baseline-v000',
                                 accountName: 'test',
                                 region     : 'us-west-1',
                                 imageId    : 'ami-12344',
                                 buildId    : 99
                               ]
                             ]]
    ])
    deployCanaryStage.parentStageId = parentStageId
    def monitorCanaryStage = new Stage<>(pipeline, "monitorCanary", [:])

    pipeline.stages.addAll([deployCanaryStage, monitorCanaryStage])


    Map captured

    when:
    def result = task.execute(deployCanaryStage)

    then:
    1 * mineService.registerCanary(_) >> { Map c ->
      captured = c
      new Response('http:/mine', 200, 'OK', [], new TypedString('canaryId'))
    }

    then:
    1 * mineService.getCanary("canaryId") >> {
      captured
    }
    result.context.canary
    with(result.context.canary) {
      canaryDeployments.size() == 1
//      canaryDeployments[0]["@class"] == ".ClusterCanaryDeployment"
      canaryDeployments[0].canaryCluster.name == 'foo--cfieber-canary'
      canaryDeployments[0].baselineCluster.name == 'foo--cfieber-baseline'
      canaryConfig.lifetimeHours == 1
      canaryConfig.combinedCanaryResultStrategy == 'LOWEST'
    }
  }

  @Unroll
  void "should set stage timeout to #expectedTimeoutHours hours based on a lifetime of #lifetimeHours hours and warmup of #warmupMinutes minutes"() {

    given:
    def pipeline = new Pipeline(application: 'foo')
    def deployCanaryStage = new Stage<>(pipeline, DeployCanaryStage.PIPELINE_CONFIG_TYPE, [canary: canary, deployedClusterPairs: [:]])
    def parentStageId = UUID.randomUUID().toString()
    deployCanaryStage.parentStageId = parentStageId
    def monitorCanaryStage = new Stage<>(pipeline, "monitorCanary", [:])

    pipeline.stages.addAll([deployCanaryStage, monitorCanaryStage])

    when:
    def result = task.execute(deployCanaryStage)

    then:
    1 * mineService.registerCanary(_) >> { Map c ->
      new Response('http:/mine', 200, 'OK', [], new TypedString('canaryId'))
    }
    1 * mineService.getCanary("canaryId") >> canary

    result.context.stageTimeoutMs == expectedTimeoutHours * 60 * 60 * 1000

    where:
    lifetimeHours | warmupMinutes || expectedTimeoutHours
    null          | null          || 48
    "NaN"         | null          || 48
    "0"           | null          || 2
    "1"           | null          || 3
    "100"         | null          || 102
    0             | null          || 2
    1             | null          || 3
    8             | null          || 10
    1             | 60            || 4
    1             | 180           || 6

    canary = [
      canaryConfig:
        [
          lifetimeHours       : lifetimeHours,
          canaryAnalysisConfig: [
            beginCanaryAnalysisAfterMins: warmupMinutes
          ]
        ]
    ]
  }
}
