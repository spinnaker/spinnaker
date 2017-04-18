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

package com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws

import com.netflix.spinnaker.orca.clouddriver.MortService
import com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.ServerGroupCreator
import com.netflix.spinnaker.orca.kato.tasks.DeploymentDetailsAware
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Slf4j
@Component
class AmazonServerGroupCreator implements ServerGroupCreator, DeploymentDetailsAware {

  static final List<String> DEFAULT_SECURITY_GROUPS = ["nf-infrastructure", "nf-datacenter"]

  @Autowired
  MortService mortService

  @Value('${default.bake.account:default}')
  String defaultBakeAccount

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
      allowLaunchOps.each {
        ops.add([allowLaunchDescription: it])
      }
    }
    ops.add([(ServerGroupCreator.OPERATION): createServerGroupOp])
    return ops
  }

  @Override
  Optional<String> getHealthProviderName() {
    return Optional.of("Amazon")
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
    withImageFromPrecedingStage(stage, targetRegion, cloudProvider) {
      operation.amiName = operation.amiName ?: it.amiName
      operation.imageId = operation.imageId ?: it.imageId
    }

    withImageFromDeploymentDetails(stage, targetRegion, cloudProvider) {
      operation.amiName = operation.amiName ?: it.amiName
      operation.imageId = operation.imageId ?: it.imageId
    }
    if (!operation.imageId) {
      def deploymentDetails = (context.deploymentDetails ?: []) as List<Map>
      if (deploymentDetails) {
        // Because docker image ids are not region or cloud provider specific
        operation.imageId = deploymentDetails[0]?.imageId
      } else {
        // Get image id from the trigger
        if (context.cloudProvider == 'titus') {
          Map trigger = ((Pipeline) stage.execution).trigger
          if (trigger && trigger.repository && trigger.tag) {
            operation.imageId = "${trigger.repository}:${trigger.tag}".toString()
          }
          if (!operation.imageId && trigger.properties && trigger.properties.imageName) {
            operation.imageId = trigger.properties.imageName
          }
        }
      }
    }

    if (context.cloudProvider == 'titus' && stage.execution.authentication?.user) {
      operation.user = stage.execution.authentication?.user
    }

    log.info("Deploying ${operation.amiName ?: operation.imageId} to ${targetRegion}")

    if (context.account && !operation.credentials) {
      operation.credentials = context.account
    }
    operation.keyPair = (operation.keyPair ?: "nf-${operation.credentials}-keypair-a").toString()

    operation.securityGroups = operation.securityGroups ?: []

    def defaultSecurityGroupsForAccount = defaultSecurityGroups
    try {
      // Check for any explicitly provided per-account security group defaults (and use them)
      def accountDetails = mortService.getAccountDetails(operation.credentials as String)
      if (accountDetails.defaultSecurityGroups != null) {
        defaultSecurityGroupsForAccount = accountDetails.defaultSecurityGroups as List<String>
      }
    } catch (Exception e) {
      log.error("Unable to lookup default security groups", e)
    }
    addAllNonEmpty(operation.securityGroups as List<String>, defaultSecurityGroupsForAccount)

    return operation
  }

  def allowLaunchOperations(Map createServerGroupOp) {
    def ops = []
    if (createServerGroupOp.availabilityZones && createServerGroupOp.credentials != defaultBakeAccount) {
      ops.addAll(createServerGroupOp.availabilityZones.collect { String region, List<String> azs ->
        [account    : createServerGroupOp.credentials,
         credentials: defaultBakeAccount,
         region     : region,
         amiName    : createServerGroupOp.amiName]
      })

      log.info("Generated `allowLaunchDescriptions` (allowLaunchDescriptions: ${ops})")
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
