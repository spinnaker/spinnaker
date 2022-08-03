/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

import com.google.common.annotations.VisibleForTesting
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
import com.netflix.spinnaker.orca.api.pipeline.Task
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution
import com.netflix.spinnaker.orca.api.pipeline.TaskResult
import com.netflix.spinnaker.orca.clouddriver.KatoService
import com.netflix.spinnaker.orca.clouddriver.MortService
import com.netflix.spinnaker.orca.clouddriver.MortService.SecurityGroup
import com.netflix.spinnaker.orca.clouddriver.OortService
import groovy.transform.PackageScope
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import javax.annotation.Nonnull

import static com.netflix.spinnaker.orca.clouddriver.MortService.VPC.findForRegionAndAccount

@Component
class CopyAmazonLoadBalancerTask implements Task {
  @Autowired
  KatoService katoService

  @Autowired
  OortService oortService

  @Autowired
  MortService mortService

  @Nonnull
  @Override
  TaskResult execute(@Nonnull StageExecution stage) {
    def operation = stage.mapTo(StageData)
    def currentLoadBalancer = oortService.getLoadBalancerDetails(
      "aws", operation.credentials, operation.region, operation.name
    ).getAt(0)

    if (!currentLoadBalancer) {
      throw new IllegalStateException("Load balancer does not exist (name: ${operation.name}, account: ${operation.credentials}, region: ${operation.region})")
    }

    def securityGroups = getSecurityGroupNames(currentLoadBalancer)

    def allVPCs = mortService.getVPCs()
    def operations = operation.targets.collect { StageData.Target target ->
      if (target.availabilityZones.size() != 1) {
        throw new IllegalStateException("Must specify one (and only one) region/availability zones per target")
      }

      return [
        upsertAmazonLoadBalancerDescription: [
          healthCheck       : currentLoadBalancer.healthCheck.target,
          healthInterval    : currentLoadBalancer.healthCheck.interval,
          healthTimeout     : currentLoadBalancer.healthCheck.timeout,
          unhealthyThreshold: currentLoadBalancer.healthCheck.unhealthyThreshold,
          healthyThreshold  : currentLoadBalancer.healthCheck.healthyThreshold,
          listeners         : currentLoadBalancer.listenerDescriptions.collect {
            [
              externalProtocol: it.listener.protocol,
              externalPort    : it.listener.loadBalancerPort,
              internalProtocol: it.listener.instanceProtocol,
              internalPort    : it.listener.instancePort,
              sslCertificateId: it.listener.sslcertificateId
            ]
          },
          availabilityZones : target.availabilityZones,
          securityGroups    : securityGroups.collect {
            def mappings = target.securityGroupMappings as Map<String, String> ?: [:]
            return mappings[it] ?: it
          },
          name              : target.name ?: currentLoadBalancer.loadBalancerName,
          vpcId             : target.vpcId ? findForRegionAndAccount(allVPCs, target.vpcId, target.region, target.credentials).id : null,
          subnetType        : target.subnetType,
          region            : target.region,
          credentials       : target.credentials
        ]
      ]
    }

    def taskId = katoService.requestOperations(operations)

    Map outputs = [
      "notification.type": "upsertamazonloadbalancer",
      "kato.last.task.id": taskId,
      "targets"          : operations.collect {
        [
          credentials      : it.upsertAmazonLoadBalancerDescription.credentials,
          availabilityZones: it.upsertAmazonLoadBalancerDescription.availabilityZones,
          vpcId            : it.upsertAmazonLoadBalancerDescription.vpcId,
          name             : it.upsertAmazonLoadBalancerDescription.name
        ]
      }
    ]

    TaskResult.builder(ExecutionStatus.SUCCEEDED).context(outputs).build()
  }

  @VisibleForTesting
  @PackageScope
  Collection<String> getSecurityGroupNames(Map currentLoadBalancer) {
    currentLoadBalancer.securityGroups.collect { String securityGroupId ->
      SecurityGroup.findById(mortService, securityGroupId).name
    }
  }

  static class StageData {
    String credentials
    String region
    String vpcId
    String name

    Collection<Target> targets

    static class Target {
      String credentials
      Map<String, Collection<String>> availabilityZones
      String vpcId
      String name
      String subnetType = "none"
      Map<String, String> securityGroupMappings

      String getRegion() {
        return availabilityZones.keySet()[0]
      }
    }
  }
}
