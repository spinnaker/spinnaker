/*
 * Copyright (c) 2017 Oracle America, Inc.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */
package com.netflix.spinnaker.clouddriver.oraclebmcs.deploy.handler

import com.netflix.frigga.Names
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.deploy.DeployDescription
import com.netflix.spinnaker.clouddriver.deploy.DeployHandler
import com.netflix.spinnaker.clouddriver.deploy.DeploymentResult
import com.netflix.spinnaker.clouddriver.model.ServerGroup
import com.netflix.spinnaker.clouddriver.oraclebmcs.deploy.OracleBMCSServerGroupNameResolver
import com.netflix.spinnaker.clouddriver.oraclebmcs.deploy.OracleBMCSWorkRequestPoller
import com.netflix.spinnaker.clouddriver.oraclebmcs.deploy.description.BasicOracleBMCSDeployDescription
import com.netflix.spinnaker.clouddriver.oraclebmcs.model.OracleBMCSInstance
import com.netflix.spinnaker.clouddriver.oraclebmcs.model.OracleBMCSServerGroup
import com.netflix.spinnaker.clouddriver.oraclebmcs.provider.view.OracleBMCSClusterProvider
import com.netflix.spinnaker.clouddriver.oraclebmcs.service.servergroup.OracleBMCSServerGroupService
import com.oracle.bmc.core.requests.GetVnicRequest
import com.oracle.bmc.core.requests.ListVnicAttachmentsRequest
import com.oracle.bmc.loadbalancer.model.BackendDetails
import com.oracle.bmc.loadbalancer.model.CreateBackendSetDetails
import com.oracle.bmc.loadbalancer.model.HealthCheckerDetails
import com.oracle.bmc.loadbalancer.model.UpdateListenerDetails
import com.oracle.bmc.loadbalancer.requests.CreateBackendSetRequest
import com.oracle.bmc.loadbalancer.requests.GetLoadBalancerRequest
import com.oracle.bmc.loadbalancer.requests.UpdateListenerRequest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import java.util.concurrent.TimeUnit

@Component
class BasicOracleBMCSDeployHandler implements DeployHandler<BasicOracleBMCSDeployDescription> {

  private static final String BASE_PHASE = "DEPLOY"

  @Autowired
  OracleBMCSServerGroupService oracleBMCSServerGroupService

  @Autowired
  OracleBMCSClusterProvider clusterProvider

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  @Override
  boolean handles(DeployDescription description) {
    description instanceof BasicOracleBMCSDeployDescription
  }

  @Override
  DeploymentResult handle(BasicOracleBMCSDeployDescription description, List priorOutputs) {
    def region = description.region
    def serverGroupNameResolver = new OracleBMCSServerGroupNameResolver(oracleBMCSServerGroupService, description.credentials, region)
    def clusterName = serverGroupNameResolver.combineAppStackDetail(description.application, description.stack, description.freeFormDetails)

    task.updateStatus BASE_PHASE, "Initializing creation of server group for cluster $clusterName ..."

    task.updateStatus BASE_PHASE, "Looking up next sequence..."

    def serverGroupName = serverGroupNameResolver.resolveNextServerGroupName(description.application, description.stack, description.freeFormDetails, false)

    task.updateStatus BASE_PHASE, "Produced server group name: $serverGroupName"

    task.updateStatus BASE_PHASE, "Composing server group $serverGroupName..."

    Map<String, Object> launchConfig = [
      "availabilityDomain": description.availabilityDomain,
      "compartmentId"     : description.credentials.compartmentId,
      "imageId"           : description.imageId,
      "shape"             : description.shape,
      "vpcId"             : description.vpcId,
      "subnetId"          : description.subnetId,
      "createdTime"       : System.currentTimeMillis()
    ]

    def sg = new OracleBMCSServerGroup(
      name: serverGroupName,
      region: description.region,
      zone: description.availabilityDomain,
      launchConfig: launchConfig,
      targetSize: description.capacity.desired,
      credentials: description.credentials,
      loadBalancerId: description.loadBalancerId
    )

    oracleBMCSServerGroupService.createServerGroup(sg)

    task.updateStatus BASE_PHASE, "Done creating server group $serverGroupName."

    if (description.loadBalancerId) {
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
      sgView.instances.each { instance ->
        def vnicAttachRs = description.credentials.computeClient.listVnicAttachments(ListVnicAttachmentsRequest.builder()
          .compartmentId(description.credentials.compartmentId)
          .instanceId(((OracleBMCSInstance) instance).id)
          .build())
        vnicAttachRs.items.each { vnicAttach ->
          def vnic = description.credentials.networkClient.getVnic(GetVnicRequest.builder()
            .vnicId(vnicAttach.vnicId).build()).vnic
          ips << vnic.privateIp
        }
      }

      // get LB
      task.updateStatus BASE_PHASE, "Getting loadbalancer details"
      def lb = description.credentials.loadBalancerClient.getLoadBalancer(GetLoadBalancerRequest.builder().loadBalancerId(description.loadBalancerId).build()).loadBalancer

      // use backend-template to add a new backend set
      def names = Names.parseName(sg.name)
      def backendTemplate = lb.backendSets.get(names.cluster + '-template')
      def backend = CreateBackendSetDetails.builder()
        .healthChecker(HealthCheckerDetails.builder()
        .protocol(backendTemplate.healthChecker.protocol)
        .port(backendTemplate.healthChecker.port)
        .intervalInMillis(backendTemplate.healthChecker.intervalInMillis)
        .retries(backendTemplate.healthChecker.retries)
        .timeoutInMillis(backendTemplate.healthChecker.timeoutInMillis)
        .urlPath(backendTemplate.healthChecker.urlPath)
        .returnCode(backendTemplate.healthChecker.returnCode)
        .responseBodyRegex(backendTemplate.healthChecker.responseBodyRegex)
        .build())
        .policy(backendTemplate.policy)
        .backends(ips.collect { ip ->
        BackendDetails.builder().ipAddress(ip).port(backendTemplate.healthChecker.port).build()
      })
        .name(sg.name)
        .build()

      // update lb to point to that backend set
      task.updateStatus BASE_PHASE, "Creating backend set ${backend.name}"
      def rs = description.credentials.loadBalancerClient.createBackendSet(CreateBackendSetRequest.builder()
        .loadBalancerId(lb.id)
        .createBackendSetDetails(backend).build())

      // wait for backend set to be created
      OracleBMCSWorkRequestPoller.poll(rs.getOpcWorkRequestId(), BASE_PHASE, task, description.credentials.loadBalancerClient)

      // update listener
      def currentListener = lb.listeners.get(names.cluster)
      task.updateStatus BASE_PHASE, "Updating listener ${currentListener.name} to point to backend set ${backend.name}"
      def ulrs = description.credentials.loadBalancerClient.updateListener(UpdateListenerRequest.builder()
        .listenerName(currentListener.name)
        .loadBalancerId(lb.id)
        .updateListenerDetails(UpdateListenerDetails.builder()
        .port(currentListener.port)
        .defaultBackendSetName(backend.name)
        .protocol(currentListener.protocol)
        .build())
        .build())

      // wait for listener to be updated
      OracleBMCSWorkRequestPoller.poll(ulrs.getOpcWorkRequestId(), BASE_PHASE, task, description.credentials.loadBalancerClient)
    }

    DeploymentResult deploymentResult = new DeploymentResult()
    deploymentResult.serverGroupNames = ["$region:$serverGroupName".toString()]
    deploymentResult.serverGroupNameByRegion[region] = serverGroupName
    return deploymentResult
  }
}
