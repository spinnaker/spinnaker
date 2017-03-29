/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.tasks.servergroup

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.RetryableTask
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.clouddriver.KatoService
import com.netflix.spinnaker.orca.clouddriver.tasks.AbstractCloudProviderAwareTask
import com.netflix.spinnaker.orca.clouddriver.utils.HealthHelper
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Slf4j
@Component
class CreateServerGroupTask extends AbstractCloudProviderAwareTask implements RetryableTask {

  @Autowired
  KatoService kato

  @Autowired
  List<ServerGroupCreator> serverGroupCreators

  long backoffPeriod = 2000
  long timeout = 60000

  @Override
  TaskResult execute(Stage stage) {
    String credentials = getCredentials(stage)
    String cloudProvider = getCloudProvider(stage)
    def creator = serverGroupCreators.find { it.cloudProvider == cloudProvider }
    if (!creator) {
      throw new IllegalStateException("ServerGroupCreator not found for cloudProvider $cloudProvider")
    }

    def ops = creator.getOperations(stage)
    def taskId = kato.requestOperations(cloudProvider, ops).toBlocking().first()

    Map outputs = [
        "notification.type"   : "createdeploy",
        "kato.result.expected": creator.katoResultExpected,
        "kato.last.task.id"   : taskId,
        "deploy.account.name" : credentials
    ]

    if (stage.context.suspendedProcesses?.contains("AddToLoadBalancer")) {
      outputs.interestingHealthProviderNames = HealthHelper.getInterestingHealthProviderNames(stage, ["Amazon"])
    }

    return new TaskResult(ExecutionStatus.SUCCEEDED, outputs)
  }
}
