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
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.kato.api.KatoService
import com.netflix.spinnaker.orca.kato.api.TaskId
import com.netflix.spinnaker.orca.mine.Canary
import com.netflix.spinnaker.orca.mine.Health
import com.netflix.spinnaker.orca.mine.MineService
import com.netflix.spinnaker.orca.mine.Status
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.PipelineStage
import spock.lang.Ignore
import spock.lang.Specification
import spock.lang.Subject

class MonitorCanaryTaskSpec extends Specification {
  MineService mineService = Mock(MineService)
  KatoService katoService = Mock(KatoService)
  ObjectMapper objectMapper = new ObjectMapper()
  ResultSerializationHelper resultSerializationHelper = new ResultSerializationHelper(objectMapper: objectMapper)

  @Subject MonitorCanaryTask task = new MonitorCanaryTask(mineService: mineService, katoService: katoService, resultSerializationHelper: resultSerializationHelper)

  def 'should retry until completion'() {
    setup:
    def canaryConf = [
      id: UUID.randomUUID().toString(),
      owner: [name: 'cfieber', email: 'cfieber@netflix.com'],
      canaryDeployments: [[canaryCluster: [:], baselineCluster: [:]]],
      status: [status: 'RUNNING', complete: false],
      health: [health: 'HEALTHY'],
      launchedDate: System.currentTimeMillis(),
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
    ]
    def stage = new PipelineStage(new Pipeline(application: "foo"), "canary", [canary: canaryConf])
    Canary canary = stage.mapTo('/canary', Canary)
    canary.status = resultStatus

    when:
    TaskResult result = task.execute(stage)

    then:
    1 * mineService.getCanary(stage.context.canary.id) >> canary
    result.status == executionStatus

    where:
    resultStatus                                   | executionStatus
    new Status('RUNNING', false) | ExecutionStatus.RUNNING
    new Status('COMPLETE', true) | ExecutionStatus.SUCCEEDED
    new Status('FAILED', true)  | ExecutionStatus.SUCCEEDED
  }

  def 'should perform a scaleup'() {
    setup:
    def canaryConf = [
      id: UUID.randomUUID().toString(),
      owner: [name: 'cfieber', email: 'cfieber@netflix.com'],
      canaryDeployments: [
        [canaryCluster: [name: 'foo--cfieber-canary', accountName: 'test', region: 'us-west-1'],
         baselineCluster: [name: 'foo--cfieber-baseline', accountName: 'test', region: 'us-west-1']
        ]],
      status: [status: 'RUNNING', complete: false],
      health: [health: 'HEALTHY'],
      launchedDate: System.currentTimeMillis() - 61000,
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
    ]
    def stageCtx = [:]
    def stage = new PipelineStage(new Pipeline(application: "foo"), "canary", stageCtx)
    stageCtx.putAll([
      canary: canaryConf,
      scaleUp: [
        enabled: true,
        capacity: 3,
        delay: 1
      ],
      deployedClusterPairs: [[
        canaryStage: stage.id,
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
      ]

      ]
    ])
    Canary canary = stage.mapTo('/canary', Canary)

    when:
    TaskResult result = task.execute(stage)

    then:
    1 * mineService.getCanary(stage.context.canary.id) >> canary
    1 * katoService.requestOperations({ ops ->
      ops.size() == 2 &&
      ops.find { it.resizeAsgDescription.asgName == 'foo--cfieber-canary-v000' }
      ops.find { it.resizeAsgDescription.asgName == 'foo--cfieber-baseline-v000' } }) >> rx.Observable.just(new TaskId('blah'))
  }

  def 'should disable unhealthy canary'() {
    setup:
    def canaryConf = [
      id: UUID.randomUUID().toString(),
      owner: [name: 'cfieber', email: 'cfieber@netflix.com'],
      canaryDeployments: [
        [canaryCluster: [name: 'foo--cfieber-canary', accountName: 'test', region: 'us-west-1'],
         baselineCluster: [name: 'foo--cfieber-baseline', accountName: 'test', region: 'us-west-1']
        ]],
      status: [status: 'RUNNING', complete: false],
      health: [health: Health.UNHEALTHY],
      launchedDate: System.currentTimeMillis() - 61000,
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
    ]
    def stageCtx = [:]
    def stage = new PipelineStage(new Pipeline(application: "foo"), "canary", stageCtx)
    stageCtx.putAll([
      canary: canaryConf,
      deployedClusterPairs: [[
                               canaryStage: stage.id,
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
                             ]
    ]])

    Canary  canary = stage.mapTo('/canary', Canary)
    Canary terminated = stage.mapTo('/canary', Canary)
    terminated.status.status = 'DISABLED'
    terminated.status.complete = false

    when:
    TaskResult result = task.execute(stage)

    then:
    1 * mineService.getCanary(canary.id) >> canary
    1 * katoService.requestOperations({ ops ->
      ops.size() == 2 &&
      ops.find { it.disableAsgDescription.asgName == 'foo--cfieber-canary-v000' }
      ops.find { it.disableAsgDescription.asgName == 'foo--cfieber-baseline-v000' } }) >> rx.Observable.just(new TaskId('blah'))
  }

}
