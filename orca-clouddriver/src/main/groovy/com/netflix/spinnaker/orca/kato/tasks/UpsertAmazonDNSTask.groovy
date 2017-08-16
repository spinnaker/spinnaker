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

import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.clouddriver.KatoService
import com.netflix.spinnaker.orca.clouddriver.pipeline.loadbalancer.UpsertLoadBalancerStage
import com.netflix.spinnaker.orca.kato.pipeline.UpsertAmazonLoadBalancerStage
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import static com.netflix.spinnaker.orca.ExecutionStatus.SUCCEEDED

@Component
@CompileStatic
class UpsertAmazonDNSTask implements Task {

  @Autowired
  KatoService kato

  @Override
  TaskResult execute(Stage stage) {
    def operation = [
      type          : stage.context.recordType,
      name          : stage.context.name,
      hostedZoneName: stage.context.hostedZone,
      credentials   : stage.context.credentials
    ]

    def upsertElbStage = stage.ancestors().find {
      it.type in [UpsertAmazonLoadBalancerStage.PIPELINE_CONFIG_TYPE, UpsertLoadBalancerStage.PIPELINE_CONFIG_TYPE]
    }

    if (upsertElbStage) {
      operation.target = upsertElbStage.context.dnsName
    } else {
      operation.target = stage.context.target
    }

    def taskId = kato.requestOperations([[upsertAmazonDNSDescription: operation]]).toBlocking().first()

    Map outputs = [
      "notification.type": "upsertamazondns",
      "kato.last.task.id": taskId
    ]

    return new TaskResult(SUCCEEDED, outputs)
  }
}
