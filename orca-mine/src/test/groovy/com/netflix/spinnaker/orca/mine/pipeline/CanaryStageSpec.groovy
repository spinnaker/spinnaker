/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.mine.pipeline

import com.netflix.spinnaker.orca.CancellableStage.Result
import com.netflix.spinnaker.orca.RetrySupport
import com.netflix.spinnaker.orca.clouddriver.model.TaskId
import com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.DestroyServerGroupTask
import com.netflix.spinnaker.orca.clouddriver.KatoService
import com.netflix.spinnaker.orca.clouddriver.utils.OortHelper
import com.netflix.spinnaker.orca.pipeline.model.Stage
import rx.Observable
import spock.lang.Specification
import spock.lang.Unroll

import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.stage

class CanaryStageSpec extends Specification {

  @Unroll
  void "cancel destroys canary/baseline if found and were deployed during canary stage"() {
    given:
    Map stageContext = [
      clusterDisableWaitTime: 0,
      clusterPairs: [
        [
          baseline: [application: "app", stack: "stack1", freeFormDetails: "baseline", region: "us-east-1", account: "test"],
          canary: [application: "app", stack: "stack1", freeFormDetails: "canary", region: "us-east-1", account: "test"]
        ]
      ]
    ]

    def disableOperation = [
      [disableServerGroup:
         [
           serverGroupName: "app-stack1-baseline-v003", region: "us-east-1", credentials: "test",
           cloudProvider  : "aws", remainingEnabledServerGroups: 0, preferLargerOverNewer: false
         ]
      ],
      [disableServerGroup:
         [
           serverGroupName: "app-stack1-canary-v003", region: "us-east-1", credentials: "test",
           cloudProvider  : "aws", remainingEnabledServerGroups: 0, preferLargerOverNewer: false
         ]
      ]
    ]

    TaskId taskId = new TaskId(UUID.randomUUID().toString())

    Stage canceledStage = stage {
      context = stageContext
      startTime = 5
      endTime = 10
    }

    OortHelper oortHelper = Mock(OortHelper)
    KatoService katoService = Mock(KatoService)
    DestroyServerGroupTask destroyServerGroupTask = Mock(DestroyServerGroupTask)

    CanaryStage canaryStage = new CanaryStage(
      oortHelper: oortHelper,
      katoService: katoService,
      destroyServerGroupTask: destroyServerGroupTask,
      retrySupport: Spy(RetrySupport) {
        _ * sleep(_) >> { /* do nothing */ }
      }
    )

    when:
    Result result = canaryStage.cancel(canceledStage)

    then:
    result.details.destroyContexts.size() == destroyedServerGroups
    1 * oortHelper.getCluster("app", "test", "app-stack1-baseline", "aws") >> [
      serverGroups: [[region: "us-east-1", createdTime: createdTime, name: "app-stack1-baseline-v003"]]
    ]
    1 * oortHelper.getCluster("app", "test", "app-stack1-canary", "aws") >> [
      serverGroups: [[region: "us-east-1", createdTime: createdTime, name: "app-stack1-canary-v003"]]
    ]

    disableOps * katoService.requestOperations("aws", disableOperation) >> { Observable.from(taskId) }

    where:
    createdTime | disableOps | destroyedServerGroups
    4           | 0          | 0
    5           | 0          | 0
    6           | 1          | 2
    10          | 1          | 2
    5010        | 0          | 0
    5011        | 0          | 0

  }

}
