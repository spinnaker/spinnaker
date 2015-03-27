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

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.kato.api.KatoService
import com.netflix.spinnaker.orca.kato.api.TaskId
import com.netflix.spinnaker.orca.mine.MineService
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.PipelineStage
import spock.lang.Specification
import spock.lang.Subject

class MonitorCanaryTaskSpec extends Specification {
  MineService mineService = Mock(MineService)
  KatoService katoService = Mock(KatoService)

  @Subject MonitorCanaryTask task = new MonitorCanaryTask(mineService: mineService, katoService: katoService)

  def 'should retry until completion'() {
    setup:
    def canaryConf = [
      id: UUID.randomUUID().toString(),
      owner: [name: 'cfieber', email: 'cfieber@netflix.com'],
      canaryDeployments: [[canaryCluster: [], baselineCluster: []]],
      status: [status: 'RUNNING', complete: false],
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

    when:
    TaskResult result = task.execute(stage)

    then:
    1 * mineService.checkCanaryStatus(stage.context.canary.id) >> (canaryConf + resultStatus)
    result.status == executionStatus

    where:
    resultStatus                                   | executionStatus
    [status: [status: 'RUNNING', complete: false]] | ExecutionStatus.RUNNING
    [status: [status: 'COMPLETE', complete: true]] | ExecutionStatus.SUCCEEDED
    [status: [status: 'FAILED', complete: true]]   | ExecutionStatus.SUCCEEDED
  }

  def 'should perform a scaleup'() {
    setup:
    def canaryConf = [
      id: UUID.randomUUID().toString(),
      owner: [name: 'cfieber', email: 'cfieber@netflix.com'],
      canaryDeployments: [
        [canaryCluster: [name: 'foo--canary', accountName: 'prod', region: 'us-east-1'],
         baselineCluster: [name: 'foo--baseline', accountName: 'prod', region: 'us-east-1']
        ]],
      status: [status: 'RUNNING', complete: false],
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
    def stage = new PipelineStage(new Pipeline(application: "foo"), "canary", [
      canary: canaryConf,
      'deploy.server.groups': ['us-east-1': ['foo--canary-v001', 'foo--baseline-v001']],
      scaleUp: [
        enabled: true,
        capacity: 3,
        delay: 1
      ]
    ])

    when:
    TaskResult result = task.execute(stage)

    then:
    1 * mineService.checkCanaryStatus(stage.context.canary.id) >> canaryConf
    1 * katoService.requestOperations({ ops ->
      ops.size() == 2 &&
      ops.find { it.resizeAsgDescription.asgName == 'foo--canary-v001' }
      ops.find { it.resizeAsgDescription.asgName == 'foo--baseline-v001' } }) >> rx.Observable.just(new TaskId('blah'))

  }
}
