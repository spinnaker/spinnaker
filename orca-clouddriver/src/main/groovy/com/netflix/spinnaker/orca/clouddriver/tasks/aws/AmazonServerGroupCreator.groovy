/*
 * Copyright 2015 Google, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.tasks.aws

import com.netflix.spinnaker.orca.clouddriver.tasks.ServerGroupCreator
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Slf4j
@Component
class AmazonServerGroupCreator implements ServerGroupCreator {

  static final List<String> DEFAULT_VPC_SECURITY_GROUPS = ["nf-infrastructure-vpc", "nf-datacenter-vpc"]
  static final List<String> DEFAULT_SECURITY_GROUPS = ["nf-infrastructure", "nf-datacenter"]

  @Value('${default.bake.account:default}')
  String defaultBakeAccount

  @Value('${default.vpc.securityGroups:#{T(com.netflix.spinnaker.orca.kato.tasks.CreateDeployTask).DEFAULT_VPC_SECURITY_GROUPS}}')
  List<String> defaultVpcSecurityGroups = DEFAULT_VPC_SECURITY_GROUPS

  @Value('${default.securityGroups:#{T(com.netflix.spinnaker.orca.kato.tasks.CreateDeployTask).DEFAULT_SECURITY_GROUPS}}')
  List<String> defaultSecurityGroups = DEFAULT_SECURITY_GROUPS

  boolean katoResultExpected = true
  String cloudProvider = "aws"

  @Override
  List<Map> getOperations(Stage stage) {
    def ops = []
    def createServerGroupOp = createServerGroupOperation(stage)
    def allowLaunchOps = allowLaunchOperations(createServerGroupOp)
    if (allowLaunchOps) {
      allowLaunchOps.each{
        ops.add([allowLaunchDescription: it])
      }
    }
    ops.add([(ServerGroupCreator.OPERATION): createServerGroupOp])
    return ops
  }

  def createServerGroupOperation(Stage stage) {
    def operation = [:]
    def context = stage.context

    if (context.containsKey("cluster")) {
      operation.putAll(context.cluster as Map)
    } else {
      operation.putAll(context)
    }

    def targetRegion = operation.region ?: (operation.availabilityZones as Map<String, Object>).keySet()[0]
    def deploymentDetails = (context.deploymentDetails ?: []) as List<Map>
    if (!operation.amiName && deploymentDetails) {
      operation.amiName = deploymentDetails.find { it.region == targetRegion }?.ami
    }
    if (!operation.imageId && deploymentDetails) {
      operation.imageId = deploymentDetails[0]?.imageId
      // Because docker image ids are not region or cloud provider specific
    }

    log.info("Deploying ${operation.amiName ?: operation.imageId} to ${targetRegion}")

    if (context.account && !operation.credentials) {
      operation.credentials = context.account
    }
    operation.keyPair = (operation.keyPair ?: "nf-${operation.credentials}-keypair-a").toString()

    operation.securityGroups = operation.securityGroups ?: []
    //TODO(cfieber)- remove the VPC special case asap
    if (operation.subnetType && !operation.subnetType.contains('vpc0')) {
      addAllNonEmpty(operation.securityGroups, defaultVpcSecurityGroups)
    } else {
      addAllNonEmpty(operation.securityGroups, defaultSecurityGroups)
    }

    return operation
  }

  def allowLaunchOperations(Map createServerGroupOp) {
    def ops = []
    if (createServerGroupOp.credentials != defaultBakeAccount) {
      if (createServerGroupOp.availabilityZones) {
        ops.addAll(createServerGroupOp.availabilityZones.collect { String region, List<String> azs ->
          [account    : createServerGroupOp.credentials,
           credentials: defaultBakeAccount,
           region     : region,
           amiName    : createServerGroupOp.amiName]
        })

        log.info("Generated `allowLaunchDescriptions` (allowLaunchDescriptions: ${ops})")
      }
    }
    return ops
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
