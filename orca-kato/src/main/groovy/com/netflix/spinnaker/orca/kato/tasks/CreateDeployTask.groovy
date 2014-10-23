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

import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.PipelineStatus
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.kato.api.KatoService
import com.netflix.spinnaker.orca.kato.api.TaskId
import com.netflix.spinnaker.orca.kato.api.ops.AllowLaunchOperation
import com.netflix.spinnaker.orca.pipeline.Stage
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value

@CompileStatic
class CreateDeployTask implements Task {

  @Autowired
  KatoService kato

  @Autowired
  ObjectMapper mapper

  @Value('${default.bake.account:test}')
  String defaultBakeAccount

  @Override
  TaskResult execute(Stage stage) {
    def deployOperations = deployOperationFromContext(stage)
    def taskId = deploy(deployOperations)
    new DefaultTaskResult(PipelineStatus.SUCCEEDED, [
      "notification.type": "createdeploy",
            "kato.last.task.id"  : taskId,
            "kato.task.id"       : taskId, // TODO retire this.
            "deploy.account.name": deployOperations.credentials,
    ])
  }

  private Map deployOperationFromContext(Stage stage) {
    def operation = [:]
    if (stage.context.containsKey("cluster")) {
      operation.putAll(stage.context.cluster as Map)
    } else {
      operation.putAll(stage.context)
    }
    // TODO This logic is not great... we should have a mechanism to discriminate on stage type, versus how it was built from config.
    if (stage.preceding("bake")?.context?.ami) {
      operation.amiName = stage.preceding("bake").context.ami
    } else if (stage.preceding("opinionatedBake")?.context?.ami) {
      operation.amiName = stage.preceding("opinionatedBake").context.ami
    }
    if (stage.context.account && !operation.credentials) {
      operation.credentials = stage.context.account
    }
    operation.keyPair = (operation.keyPair ?: "nf-${operation.credentials}-keypair-a").toString()
    return operation
  }

  @CompileStatic(TypeCheckingMode.SKIP)
  private TaskId deploy(Map deployOperation) {
    deployOperation.securityGroups = deployOperation.securityGroups ?: []
    deployOperation.securityGroups.addAll((deployOperation.subnetType) ? ["nf-infrastructure-vpc", "nf-datacenter-vpc"] : ["nf-infrastructure", "nf-datacenter"])
    List<Map<String, Object>> descriptions = []

    if (deployOperation.credentials != defaultBakeAccount) {
      descriptions.addAll(deployOperation.availabilityZones.collect { String region, List<String> azs ->
        [allowLaunchDescription: convertAllowLaunch(deployOperation.credentials, defaultBakeAccount, region, deployOperation.amiName)]
      })
    }

    descriptions.add([basicAmazonDeployDescription: deployOperation])
    def result = kato.requestOperations(descriptions).toBlocking().first()
    result
  }

  private static AllowLaunchOperation convertAllowLaunch(String targetAccount, String sourceAccount, String region, String ami) {
    new AllowLaunchOperation(account: targetAccount, credentials: sourceAccount, region: region, amiName: ami)
  }
}
