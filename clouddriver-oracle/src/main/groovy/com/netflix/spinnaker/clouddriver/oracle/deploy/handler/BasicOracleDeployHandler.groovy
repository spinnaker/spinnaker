/*
 * Copyright (c) 2017, 2018, Oracle Corporation and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */
package com.netflix.spinnaker.clouddriver.oracle.deploy.handler

import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.deploy.DeployDescription
import com.netflix.spinnaker.clouddriver.deploy.DeployHandler
import com.netflix.spinnaker.clouddriver.deploy.DeploymentResult
import com.netflix.spinnaker.clouddriver.model.ServerGroup
import com.netflix.spinnaker.clouddriver.oracle.deploy.OracleServerGroupNameResolver
import com.netflix.spinnaker.clouddriver.oracle.deploy.OracleWorkRequestPoller
import com.netflix.spinnaker.clouddriver.oracle.deploy.description.BasicOracleDeployDescription
import com.netflix.spinnaker.clouddriver.oracle.model.Details
import com.netflix.spinnaker.clouddriver.oracle.model.OracleServerGroup
import com.netflix.spinnaker.clouddriver.oracle.provider.view.OracleClusterProvider
import com.netflix.spinnaker.clouddriver.oracle.service.servergroup.OracleServerGroupService
import com.oracle.bmc.core.requests.GetVnicRequest
import com.oracle.bmc.core.requests.ListVnicAttachmentsRequest
import com.oracle.bmc.loadbalancer.model.BackendDetails
import com.oracle.bmc.loadbalancer.model.BackendSet
import com.oracle.bmc.loadbalancer.model.LoadBalancer
import com.oracle.bmc.loadbalancer.model.UpdateBackendSetDetails
import com.oracle.bmc.loadbalancer.requests.GetLoadBalancerRequest
import com.oracle.bmc.loadbalancer.requests.UpdateBackendSetRequest
import com.oracle.bmc.loadbalancer.responses.UpdateBackendSetResponse
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Component
class BasicOracleDeployHandler implements DeployHandler<BasicOracleDeployDescription> {

  private static final String BASE_PHASE = "DEPLOY"

  @Autowired
  OracleServerGroupService oracleServerGroupService

  @Autowired
  OracleClusterProvider clusterProvider

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  @Override
  boolean handles(DeployDescription description) {
    description instanceof BasicOracleDeployDescription
  }

  @Override
  DeploymentResult handle(BasicOracleDeployDescription description, List priorOutputs) {
    def region = description.region
    def serverGroupNameResolver = new OracleServerGroupNameResolver(oracleServerGroupService, description.credentials, region)
    def clusterName = serverGroupNameResolver.combineAppStackDetail(description.application, description.stack, description.freeFormDetails)

    task.updateStatus BASE_PHASE, "Initializing creation of server group for cluster $clusterName ..."

    task.updateStatus BASE_PHASE, "Looking up next sequence..."

    def serverGroupName = serverGroupNameResolver.resolveNextServerGroupName(description.application, description.stack, description.freeFormDetails, false)

    task.updateStatus BASE_PHASE, "Produced server group name: $serverGroupName"

    Map<String, Object> launchConfig = [
      "availabilityDomain": description.availabilityDomain,
      "compartmentId"     : description.credentials.compartmentId,
      "imageId"           : description.imageId,
      "shape"             : description.shape,
      "vpcId"             : description.vpcId,
      "subnetId"          : description.subnetId,
      "sshAuthorizedKeys" : description.sshAuthorizedKeys,
      "createdTime"       : System.currentTimeMillis()
    ]
    int targetSize = description.targetSize?: (description.capacity?.desired?:0)
    task.updateStatus BASE_PHASE, "Composing server group $serverGroupName with $targetSize instance(s) "

    def sg = new OracleServerGroup(
      name: serverGroupName,
      region: description.region,
      zone: description.availabilityDomain,
      launchConfig: launchConfig,
      targetSize: targetSize,
      credentials: description.credentials,
      loadBalancerId: description.loadBalancerId
    )

    oracleServerGroupService.createServerGroup(task, sg)

    task.updateStatus BASE_PHASE, "Done creating server group $serverGroupName."

    if (description.loadBalancerId) {
      // get LB
      LoadBalancer lb = description.credentials.loadBalancerClient.getLoadBalancer(
        GetLoadBalancerRequest.builder().loadBalancerId(description.loadBalancerId).build()).loadBalancer

      task.updateStatus BASE_PHASE, "Updating LoadBalancer ${lb.displayName} with backendSet ${description.backendSetName}"
      // wait for instances to go into running state
      ServerGroup sgView
      long finishBy = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(30)
      boolean allUp = false
      while (!allUp && System.currentTimeMillis() < finishBy) {
        sgView = clusterProvider.getServerGroup(sg.credentials.name, sg.region, sg.name)
        if (sgView && (sgView.instanceCounts.up == sgView.instanceCounts.total)) {
          task.updateStatus BASE_PHASE, "All instances are Up"
          allUp = true
          break
        }
        task.updateStatus BASE_PHASE, "Waiting for server group instances to get to Up state"
        Thread.sleep(5000)
      }
      if (!allUp) {
        task.updateStatus(BASE_PHASE, "Timed out waiting for server group instances to get to Up state")
        task.fail()
        return
      }

      // get their ip addresses
      task.updateStatus BASE_PHASE, "Looking up instance IP addresses"
      List<String> ips = []
      sg.instances.each { instance ->
        def vnicAttachRs = description.credentials.computeClient.listVnicAttachments(ListVnicAttachmentsRequest.builder()
          .compartmentId(description.credentials.compartmentId)
          .instanceId(instance.id)
          .build())
        vnicAttachRs.items.each { vnicAttach ->
          def vnic = description.credentials.networkClient.getVnic(GetVnicRequest.builder()
            .vnicId(vnicAttach.vnicId).build()).vnic
          if (vnic.privateIp) {
            instance.privateIp = vnic.privateIp
            ips << vnic.privateIp
          }
        }
      }
      sg.backendSetName = description.backendSetName
      task.updateStatus BASE_PHASE, "Adding IP addresses ${ips} to ${description.backendSetName}"
      oracleServerGroupService.updateServerGroup(sg)
      // update listener and backendSet
      BackendSet defaultBackendSet = lb.backendSets.get(description.backendSetName)
      // new backends from the serverGroup
      List<BackendDetails> backends = ips.collect { ip ->
        BackendDetails.builder().ipAddress(ip).port(defaultBackendSet.healthChecker.port).build()
      }
      //merge with existing backendSet
      defaultBackendSet.backends.each { existingBackend ->
        backends << Details.of(existingBackend)
      }
        
      UpdateBackendSetDetails updateDetails = UpdateBackendSetDetails.builder()
        .policy(defaultBackendSet.policy)
        .healthChecker(Details.of(defaultBackendSet.healthChecker))
        .backends(backends).build()
      task.updateStatus BASE_PHASE, "Updating backendSet ${description.backendSetName}"
      UpdateBackendSetResponse updateRes = description.credentials.loadBalancerClient.updateBackendSet(
        UpdateBackendSetRequest.builder().loadBalancerId(description.loadBalancerId)
        .backendSetName(description.backendSetName).updateBackendSetDetails(updateDetails).build())

      // wait for backend set to be created
      OracleWorkRequestPoller.poll(updateRes.getOpcWorkRequestId(), BASE_PHASE, task, description.credentials.loadBalancerClient)
    }
    DeploymentResult deploymentResult = new DeploymentResult()
    deploymentResult.serverGroupNames = ["$region:$serverGroupName".toString()]
    deploymentResult.serverGroupNameByRegion[region] = serverGroupName
    return deploymentResult
  }
}
