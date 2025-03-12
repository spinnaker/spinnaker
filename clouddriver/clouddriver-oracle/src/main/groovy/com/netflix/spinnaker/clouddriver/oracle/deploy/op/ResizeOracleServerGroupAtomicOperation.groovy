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
import com.netflix.spinnaker.clouddriver.oracle.model.Details
import com.netflix.spinnaker.clouddriver.oracle.provider.view.OracleClusterProvider
import com.netflix.spinnaker.clouddriver.oracle.service.servergroup.OracleServerGroupService
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.oracle.bmc.core.requests.GetVnicRequest
import com.oracle.bmc.core.requests.ListVnicAttachmentsRequest
import com.oracle.bmc.loadbalancer.model.BackendDetails 
import com.oracle.bmc.loadbalancer.model.BackendSet
import com.oracle.bmc.loadbalancer.model.HealthCheckerDetails
import com.oracle.bmc.loadbalancer.model.LoadBalancer
import com.oracle.bmc.loadbalancer.model.UpdateBackendSetDetails
import com.oracle.bmc.loadbalancer.requests.GetLoadBalancerRequest
import com.oracle.bmc.loadbalancer.requests.UpdateBackendSetRequest
import com.oracle.bmc.model.BmcException
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
    def app = Names.parseName(description.serverGroupName).app
    task.updateStatus BASE_PHASE, "Resizing server group: " + description.serverGroupName
    def serverGroup = oracleServerGroupService.getServerGroup(description.credentials, app, description.serverGroupName)
    int targetSize = description.targetSize?: (description.capacity?.desired?:0)
    if (targetSize == serverGroup.instances.size()) {
      task.updateStatus BASE_PHASE, description.serverGroupName + " is already running the desired number of instances"
      return
    }
    Set<String> oldGroup = serverGroup.instances.collect{it.privateIp} as Set<String>

    oracleServerGroupService.resizeServerGroup(task, description.credentials, description.serverGroupName, targetSize)

    serverGroup = oracleServerGroupService.getServerGroup(description.credentials, app, description.serverGroupName)

    if (serverGroup.loadBalancerId) {

      // wait for instances to go into running state
      ServerGroup sgView

      long finishBy = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(30)
      boolean allUp = false
      while (!allUp && System.currentTimeMillis() < finishBy) {
        sgView = clusterProvider.getServerGroup(serverGroup.credentials.name, serverGroup.region, serverGroup.name)
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
      List<String> newGroup = []
      serverGroup.instances.each { instance ->
        if (!instance.privateIp) {
          def vnicAttachRs = description.credentials.computeClient.listVnicAttachments(ListVnicAttachmentsRequest.builder()
            .compartmentId(description.credentials.compartmentId)
            .instanceId(((OracleInstance) instance).id)
            .build())
          vnicAttachRs.items.each { vnicAttach ->
            def vnic = description.credentials.networkClient.getVnic(GetVnicRequest.builder()
              .vnicId(vnicAttach.vnicId).build()).vnic
            instance.privateIp = vnic.privateIp
          }
        }
        newGroup << instance.privateIp
      }
      //update serverGroup with IPs
      oracleServerGroupService.updateServerGroup(serverGroup)

      task.updateStatus BASE_PHASE, "Getting loadbalancer details " + serverGroup?.loadBalancerId
      LoadBalancer loadBalancer = serverGroup?.loadBalancerId? description.credentials.loadBalancerClient.getLoadBalancer(
        GetLoadBalancerRequest.builder().loadBalancerId(serverGroup.loadBalancerId).build())?.getLoadBalancer() : null
      if (loadBalancer) {
        try {
          BackendSet backendSet = serverGroup.backendSetName? loadBalancer.backendSets.get(serverGroup.backendSetName) : null
          if (backendSet == null && loadBalancer.backendSets.size() == 1) {
            backendSet = loadBalancer.backendSets.values().first();
          }
          if (backendSet) {
            List<BackendDetails> backends = backendSet.backends.findAll { !oldGroup.contains(it.ipAddress) } .collect { Details.of(it) }
            newGroup.each {
              backends << BackendDetails.builder().ipAddress(it).port(backendSet.healthChecker.port).build()
            }
            UpdateBackendSetDetails.Builder details = UpdateBackendSetDetails.builder().backends(backends)
            if (backendSet.sslConfiguration) {
              details.sslConfiguration(Details.of(backendSet.sslConfiguration))
            }
            if (backendSet.sessionPersistenceConfiguration) {
              details.sessionPersistenceConfiguration(backendSet.sessionPersistenceConfiguration)
            }
            if (backendSet.healthChecker) {
              details.healthChecker(Details.of(backendSet.healthChecker))
            }
            if (backendSet.policy) {
              details.policy(backendSet.policy)
            }
            UpdateBackendSetRequest updateBackendSet = UpdateBackendSetRequest.builder()
              .loadBalancerId(serverGroup.loadBalancerId).backendSetName(backendSet.name)
              .updateBackendSetDetails(details.build()).build() 
            task.updateStatus BASE_PHASE, "Updating backendSet ${backendSet.name}"
            def updateRes = description.credentials.loadBalancerClient.updateBackendSet(updateBackendSet)
            OracleWorkRequestPoller.poll(updateRes.opcWorkRequestId, BASE_PHASE, task, description.credentials.loadBalancerClient)
          }
        } catch (BmcException e) {
          if (e.statusCode == 404) {
            task.updateStatus BASE_PHASE, "Backend set did not exist...continuing"
          } else {
            throw e
          }
        }
      }
    }
    task.updateStatus BASE_PHASE, "Completed server group resize"
    return null
  }
}
