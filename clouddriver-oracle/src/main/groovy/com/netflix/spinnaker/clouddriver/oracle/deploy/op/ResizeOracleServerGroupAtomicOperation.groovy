/*
 * Copyright (c) 2017, 2018, Oracle Corporation and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */

package com.netflix.spinnaker.clouddriver.oracle.deploy.op

import com.netflix.frigga.Names
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.model.ServerGroup
import com.netflix.spinnaker.clouddriver.oracle.deploy.OracleWorkRequestPoller
import com.netflix.spinnaker.clouddriver.oracle.deploy.description.ResizeOracleServerGroupDescription
import com.netflix.spinnaker.clouddriver.oracle.model.OracleInstance
import com.netflix.spinnaker.clouddriver.oracle.provider.view.OracleClusterProvider
import com.netflix.spinnaker.clouddriver.oracle.service.servergroup.OracleServerGroupService
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.oracle.bmc.core.requests.GetVnicRequest
import com.oracle.bmc.core.requests.ListVnicAttachmentsRequest
import com.oracle.bmc.loadbalancer.model.BackendDetails
import com.oracle.bmc.loadbalancer.model.HealthCheckerDetails
import com.oracle.bmc.loadbalancer.model.UpdateBackendSetDetails
import com.oracle.bmc.loadbalancer.requests.GetLoadBalancerRequest
import com.oracle.bmc.loadbalancer.requests.UpdateBackendSetRequest
import org.springframework.beans.factory.annotation.Autowired

import java.util.concurrent.TimeUnit

class ResizeOracleServerGroupAtomicOperation implements AtomicOperation<Void> {

  private final ResizeOracleServerGroupDescription description

  private static final String BASE_PHASE = "RESIZE_SERVER_GROUP"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  @Autowired
  OracleServerGroupService oracleServerGroupService

  @Autowired
  OracleClusterProvider clusterProvider

  ResizeOracleServerGroupAtomicOperation(ResizeOracleServerGroupDescription description) {
    this.description = description
  }

  @Override
  Void operate(List priorOutputs) {
    task.updateStatus BASE_PHASE, "Resizing server group: " + description.serverGroupName
    int targetSize = description.targetSize?: (description.capacity?.desired?:0)
    oracleServerGroupService.resizeServerGroup(task, description.credentials, description.serverGroupName, targetSize)

    // SL: sync server group instances to backendset if there is one
    def app = Names.parseName(description.serverGroupName).app
    def sg = oracleServerGroupService.getServerGroup(description.credentials, app, description.serverGroupName)

    if (sg.loadBalancerId) {

      // wait for instances to go into running state
      ServerGroup sgView

      long finishBy = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(30)
      boolean allUp = false
      while (!allUp && System.currentTimeMillis() < finishBy) {
        sgView = clusterProvider.getServerGroup(sg.credentials.name, sg.region, sg.name)
        if (sgView && (sgView.instanceCounts.up == sgView.instanceCounts.total) && (sgView.instanceCounts.total == description.capacity.desired)) {
          task.updateStatus BASE_PHASE, "All instances are Up"
          allUp = true
          break
        }
        task.updateStatus BASE_PHASE, "Waiting for server group instances to match desired total"
        Thread.sleep(5000)
      }
      if (!allUp) {
        task.updateStatus(BASE_PHASE, "Timed out waiting for server group resize")
        task.fail()
        return
      }

      // get their ip addresses
      task.updateStatus BASE_PHASE, "Looking up instance IP addresses"
      List<String> ips = []
      sgView.instances.each { instance ->
        def vnicAttachRs = description.credentials.computeClient.listVnicAttachments(ListVnicAttachmentsRequest.builder()
          .compartmentId(description.credentials.compartmentId)
          .instanceId(((OracleInstance) instance).id)
          .build())
        vnicAttachRs.items.each { vnicAttach ->
          def vnic = description.credentials.networkClient.getVnic(GetVnicRequest.builder()
            .vnicId(vnicAttach.vnicId).build()).vnic
          ips << vnic.privateIp
        }
      }

      // get LB
      task.updateStatus BASE_PHASE, "Getting loadbalancer details"
      def lb = description.credentials.loadBalancerClient.getLoadBalancer(GetLoadBalancerRequest.builder().loadBalancerId(sg.loadBalancerId).build()).loadBalancer

      // use backend-template to replace/sync backend set
      def names = Names.parseName(description.serverGroupName)
      def backendTemplate = lb.backendSets.get("${names.cluster}-template".toString())
      def backend = UpdateBackendSetRequest.builder()
        .loadBalancerId(sg.loadBalancerId)
        .backendSetName(sg.name)
        .updateBackendSetDetails(UpdateBackendSetDetails.builder()
        .backends(ips.collect { ip ->
        BackendDetails.builder().ipAddress(ip).port(backendTemplate.healthChecker.port).build()
      } as List<BackendDetails>)
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
        .build()
      ).build()

      task.updateStatus BASE_PHASE, "Updating backend set ${sg.name}"
      def rs = description.credentials.loadBalancerClient.updateBackendSet(backend)

      // wait for backend set to be updated
      OracleWorkRequestPoller.poll(rs.getOpcWorkRequestId(), BASE_PHASE, task, description.credentials.loadBalancerClient)
    }
    task.updateStatus BASE_PHASE, "Completed server group resize"
    return null
  }
}
