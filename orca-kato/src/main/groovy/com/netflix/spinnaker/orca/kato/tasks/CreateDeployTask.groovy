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
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.kato.api.KatoService
import com.netflix.spinnaker.orca.kato.api.TaskId
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Slf4j
@Component
@CompileStatic
class CreateDeployTask implements Task {

  static
  final List<String> DEFAULT_VPC_SECURITY_GROUPS = ["nf-infrastructure-vpc", "nf-datacenter-vpc"]
  static
  final List<String> DEFAULT_SECURITY_GROUPS = ["nf-infrastructure", "nf-datacenter"]

  @Autowired
  KatoService kato

  @Autowired
  ObjectMapper mapper

  @Value('${default.bake.account:test}')
  String defaultBakeAccount

  @Value('${default.vpc.securityGroups:#{T(com.netflix.spinnaker.orca.kato.tasks.CreateDeployTask).DEFAULT_VPC_SECURITY_GROUPS}}')
  List<String> defaultVpcSecurityGroups = DEFAULT_VPC_SECURITY_GROUPS

  @Value('${default.securityGroups:#{T(com.netflix.spinnaker.orca.kato.tasks.CreateDeployTask).DEFAULT_SECURITY_GROUPS}}')
  List<String> defaultSecurityGroups = DEFAULT_SECURITY_GROUPS

  @Override
  TaskResult execute(Stage stage) {
    def deployOperations = deployOperationFromContext(stage)
    def taskId = deploy(deployOperations)
    new DefaultTaskResult(ExecutionStatus.SUCCEEDED, [
      "notification.type"  : "createdeploy",
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

    def targetRegion = (operation.availabilityZones as Map<String, Object>).keySet()[0]
    def deploymentDetails = (stage.context.deploymentDetails ?: []) as List<Map>
    if (!operation.amiName && deploymentDetails) {
      operation.amiName = deploymentDetails.find { it.region == targetRegion }?.ami
    }

    log.info("Deploying ${operation.amiName} to ${targetRegion}")

    if (stage.context.account && !operation.credentials) {
      operation.credentials = stage.context.account
    }
    operation.keyPair = (operation.keyPair ?: "nf-${operation.credentials}-keypair-a").toString()
    return operation
  }

  @CompileStatic(TypeCheckingMode.SKIP)
  private TaskId deploy(Map deployOperation) {
    deployOperation.securityGroups = deployOperation.securityGroups ?: []

    if (deployOperation.subnetType) {
      addAllNonEmpty(deployOperation.securityGroups, defaultVpcSecurityGroups)
    } else {
      addAllNonEmpty(deployOperation.securityGroups, defaultSecurityGroups)
    }

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

  private
  static Map convertAllowLaunch(String targetAccount, String sourceAccount, String region, String ami) {
    [account: targetAccount, credentials: sourceAccount, region: region, amiName: ami]
  }

  private static void addAllNonEmpty(List<String> baseList, List<String> listToBeAdded) {
    if (listToBeAdded) {
      listToBeAdded.each { itemToBeAdded ->
        if (itemToBeAdded) {
          baseList << itemToBeAdded
        }
      }
    }
  }
}
