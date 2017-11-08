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

import java.util.concurrent.TimeUnit
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.clouddriver.KatoService
import com.netflix.spinnaker.orca.clouddriver.model.TaskId
import com.netflix.spinnaker.orca.mine.MineService
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class MonitorCanaryTaskSpec extends Specification {
  MineService mineService = Mock(MineService)
  KatoService katoService = Mock(KatoService)

  @Subject MonitorCanaryTask task = new MonitorCanaryTask(mineService: mineService, katoService: katoService)

  def 'should retry until completion'() {
    setup:
    def canaryConf = [
      id: UUID.randomUUID().toString(),
      owner: [name: 'cfieber', email: 'cfieber@netflix.com'],
      canaryDeployments: [[canaryCluster: [:], baselineCluster: [:]]],
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
      ],
      status: resultStatus
    ]
    def stage = new Stage(Execution.newPipeline("foo"), "canary", [canary: canaryConf])

    when:
    TaskResult result = task.execute(stage)

    then:
    1 * mineService.getCanary(stage.context.canary.id) >> canaryConf
    result.status == executionStatus

    where:
    resultStatus                         || executionStatus
    [status: 'RUNNING', complete: false] || ExecutionStatus.RUNNING
    [status: 'COMPLETE', complete: true] || ExecutionStatus.SUCCEEDED
    [status: 'FAILED', complete: true]   || ExecutionStatus.SUCCEEDED
  }

  @Unroll
  def 'should override timeout if canary duration is greater than 48 hours'() {
    setup:
    def canaryConf = [
      id: UUID.randomUUID().toString(),
      owner: [name: 'cfieber', email: 'cfieber@netflix.com'],
      canaryDeployments: [[canaryCluster: [:], baselineCluster: [:]]],
      health: [health: 'HEALTHY'],
      launchedDate: System.currentTimeMillis(),
      canaryConfig: [
        lifetimeHours: canaryLifetime,
        combinedCanaryResultStrategy: 'LOWEST',
        canarySuccessCriteria: [canaryResultScore: 95],
        canaryHealthCheckHandler: [minimumCanaryResultScore: 75],
        canaryAnalysisConfig: [
          name: 'beans',
          beginCanaryAnalysisAfterMins: 5,
          notificationHours: [1, 2],
          canaryAnalysisIntervalMins: 15
        ]
      ],
      status: [status: 'RUNNING', complete: false]
    ]
    def stage = new Stage(Execution.newPipeline("foo"), "canary", [canary: canaryConf])

    when:
    TaskResult result = task.execute(stage)

    then:
    1 * mineService.getCanary(stage.context.canary.id) >> canaryConf
    result.context.stageTimeoutMs == expected

    where:
    canaryLifetime || expected
    1              || TimeUnit.DAYS.toMillis(2)
    47             || TimeUnit.DAYS.toMillis(2)
    48             || TimeUnit.HOURS.toMillis(48) + TimeUnit.MINUTES.toMillis(15)
    50             || TimeUnit.HOURS.toMillis(50) + TimeUnit.MINUTES.toMillis(15)
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
    def canaryStageId = UUID.randomUUID().toString()
    def stage = new Stage(Execution.newPipeline("foo"), "canary", [
      canaryStageId: canaryStageId,
      canary: canaryConf,
      scaleUp: [
        enabled: true,
        capacity: 3,
        delay: 1
      ],
      deployedClusterPairs: [[
        canaryStage: canaryStageId,
        canaryCluster: [
          clusterName: 'foo--cfieber-canary',
          serverGroup: 'foo--cfieber-canary-v000',
          account: 'test',
          region: 'us-west-1',
          imageId: 'ami-12345',
          buildNumber: 100
        ],
        baselineCluster: [
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

    when:
    TaskResult result = task.execute(stage)

    then:
    1 * mineService.getCanary(stage.context.canary.id) >> canaryConf
    1 * katoService.requestOperations('aws', { ops ->
      ops.size() == 2 &&
      ops.find { it.resizeServerGroup.asgName == 'foo--cfieber-canary-v000' } &&
      ops.find { it.resizeServerGroup.asgName == 'foo--cfieber-baseline-v000' } &&
      ops.every { it.resizeServerGroup.capacity == [min:3, max: 3, desired: 3]}
      }) >> rx.Observable.just(new TaskId('blah'))
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
      health: [health: 'UNHEALTHY'],
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
    def canaryStageId = UUID.randomUUID().toString()
    def stage = new Stage(Execution.newPipeline("foo"), "canary", [
      canaryStageId: canaryStageId,
      canary: canaryConf,
      deployedClusterPairs: [[
                               canaryStage: canaryStageId,
                               canaryCluster: [
                                 clusterName: 'foo--cfieber-canary',
                                 serverGroup: 'foo--cfieber-canary-v000',
                                 account: 'test',
                                 region: 'us-west-1',
                                 imageId: 'ami-12345',
                                 buildNumber: 100
                               ],
                               baselineCluster: [
                                 clusterName: 'foo--cfieber-baseline',
                                 serverGroup: 'foo--cfieber-baseline-v000',
                                 account: 'test',
                                 region: 'us-west-1',
                                 imageId: 'ami-12344',
                                 buildNumber: 99
                               ]
                             ]
    ]])


    when:
    TaskResult result = task.execute(stage)

    then:
    1 * mineService.getCanary(canaryConf.id) >> canaryConf
    1 * katoService.requestOperations('aws', { ops ->
      ops.size() == 2 &&
      ops.find { it.disableServerGroup.asgName == 'foo--cfieber-canary-v000' }
      ops.find { it.disableServerGroup.asgName == 'foo--cfieber-baseline-v000' } }) >> rx.Observable.just(new TaskId('blah'))
  }

}
