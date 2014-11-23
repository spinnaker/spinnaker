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
package com.netflix.spinnaker.orca.kato.tasks

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.kato.api.KatoService
import com.netflix.spinnaker.orca.kato.api.ops.DeleteAmazonLoadBalancerOperation
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.beans.factory.annotation.Autowired
import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES

/**
 * Created by aglover on 9/26/14.
 */
class DeleteAmazonLoadBalancerTask implements Task {

  @Autowired
  KatoService kato

  @Autowired
  ObjectMapper mapper

  @Override
  TaskResult execute(Stage stage) {
    def deleteAmazonLoadBalancerOperation = convert(stage)

    def taskId = kato.requestOperations([[deleteAmazonLoadBalancerDescription: deleteAmazonLoadBalancerOperation]])
                     .toBlocking()
                     .first()
    Map outputs = [
      "notification.type"  : "deleteamazonloadbalancer",
      "kato.last.task.id"  : taskId,
      "kato.task.id"       : taskId, // TODO retire this.
      "delete.name"        : deleteAmazonLoadBalancerOperation.loadBalancerName,
      "delete.regions"     : deleteAmazonLoadBalancerOperation.regions.join(','),
      "delete.account.name": deleteAmazonLoadBalancerOperation.credentials
    ]
    new DefaultTaskResult(ExecutionStatus.SUCCEEDED, outputs)
  }

  DeleteAmazonLoadBalancerOperation convert(Stage stage) {
    mapper.copy()
          .configure(FAIL_ON_UNKNOWN_PROPERTIES, false)
          .convertValue(stage.context, DeleteAmazonLoadBalancerOperation)
  }
}
