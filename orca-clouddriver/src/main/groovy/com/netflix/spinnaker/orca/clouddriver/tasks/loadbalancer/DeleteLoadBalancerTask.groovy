/*
 * Copyright 2014 Netflix, Inc.
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
package com.netflix.spinnaker.orca.clouddriver.tasks.loadbalancer

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.clouddriver.KatoService
import com.netflix.spinnaker.orca.clouddriver.tasks.AbstractCloudProviderAwareTask
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

/**
 * Created by aglover on 9/26/14.
 */
@Component
class DeleteLoadBalancerTask extends AbstractCloudProviderAwareTask implements Task {

  @Autowired
  KatoService kato

  @Override
  TaskResult execute(Stage stage) {
    String cloudProvider = getCloudProvider(stage)
    String account = getCredentials(stage)

    def taskId = kato.requestOperations(cloudProvider, [[deleteLoadBalancer: stage.context]])
                     .toBlocking()
                     .first()
    Map outputs = [
        "notification.type"  : "deleteloadbalancer",
        "kato.last.task.id"  : taskId,
        "delete.name"        : stage.context.loadBalancerName,
        "delete.regions"     : stage.context.regions?.join(',') ?: [],
        "delete.account.name": account
    ]
    new TaskResult(ExecutionStatus.SUCCEEDED, outputs)
  }
}
