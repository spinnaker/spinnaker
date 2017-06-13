/*
 * Copyright (c) 2017 Oracle America, Inc.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */
package com.netflix.spinnaker.clouddriver.oraclebmcs.deploy.op

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.frigga.Names
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.oraclebmcs.deploy.OracleBMCSWorkRequestPoller
import com.netflix.spinnaker.clouddriver.oraclebmcs.deploy.description.EnableDisableOracleBMCSServerGroupDescription
import com.netflix.spinnaker.clouddriver.oraclebmcs.service.servergroup.OracleBMCSServerGroupService
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.oracle.bmc.loadbalancer.model.UpdateListenerDetails
import com.oracle.bmc.loadbalancer.requests.GetLoadBalancerRequest
import com.oracle.bmc.loadbalancer.requests.UpdateListenerRequest
import org.springframework.beans.factory.annotation.Autowired

abstract class AbstractEnableDisableAtomicOperation implements AtomicOperation<Void> {

  abstract boolean isDisable()

  abstract String getPhaseName()

  EnableDisableOracleBMCSServerGroupDescription description

  @Autowired
  OracleBMCSServerGroupService oracleBMCSServerGroupService

  @Autowired
  ObjectMapper objectMapper

  AbstractEnableDisableAtomicOperation(EnableDisableOracleBMCSServerGroupDescription description) {
    this.description = description
  }

  @Override
  Void operate(List priorOutputs) {
    String verb = disable ? 'disable' : 'enable'
    String presentParticipling = disable ? 'Disabling' : 'Enabling'

    task.updateStatus phaseName, "Initializing $verb server group operation for $description.serverGroupName in " +
      "$description.region..."

    def sg = oracleBMCSServerGroupService.getServerGroup(description.credentials, description.application, description.serverGroupName)
    def names = Names.parseName(description.serverGroupName)

    if (disable) {
      task.updateStatus phaseName, "$presentParticipling server group from Http(s) load balancers..."

      oracleBMCSServerGroupService.disableServerGroup(task, description.credentials, description.serverGroupName)

      // SL: Make sure this sg is not pointed by the listener - otherwise replace with backend template

      if (sg.loadBalancerId) {
        def lb = description.credentials.loadBalancerClient.getLoadBalancer(GetLoadBalancerRequest.builder().loadBalancerId(sg.loadBalancerId).build())
        def listener = lb.loadBalancer.listeners.get(names.cluster)
        String backendSetTemplateName = "${names.cluster}-template"
        if (listener.defaultBackendSetName == sg.name) {
          def workResponse = description.credentials.loadBalancerClient.updateListener(UpdateListenerRequest.builder()
            .listenerName(listener.name)
            .loadBalancerId(lb.loadBalancer.id)
            .updateListenerDetails(UpdateListenerDetails.builder()
            .defaultBackendSetName(backendSetTemplateName)
            .port(listener.port)
            .protocol(listener.protocol)
            .build())
            .build())
          OracleBMCSWorkRequestPoller.poll(workResponse.opcWorkRequestId, phaseName, task, description.credentials.loadBalancerClient)
        }
      }

    } else {
      task.updateStatus phaseName, "Registering server group with Http(s) load balancers..."

      oracleBMCSServerGroupService.enableServerGroup(task, description.credentials, description.serverGroupName)

      // SL: update listener to point to this SGs backend set name
      if (sg.loadBalancerId) {
        def lb = description.credentials.loadBalancerClient.getLoadBalancer(GetLoadBalancerRequest.builder().loadBalancerId(sg.loadBalancerId).build())
        def listener = lb.loadBalancer.listeners.get(names.getCluster())
        if (listener.defaultBackendSetName != sg.name) {
          def workResponse = description.credentials.loadBalancerClient.updateListener(UpdateListenerRequest.builder()
            .listenerName(listener.name)
            .loadBalancerId(lb.loadBalancer.id)
            .updateListenerDetails(UpdateListenerDetails.builder()
            .defaultBackendSetName(sg.name)
            .build())
            .build())
          OracleBMCSWorkRequestPoller.poll(workResponse.opcWorkRequestId, phaseName, task, description.credentials.loadBalancerClient)
        }
      }
    }

    task.updateStatus phaseName, "$presentParticipling server group $description.serverGroupName in $description.region..."
    task.updateStatus phaseName, "Done ${presentParticipling.toLowerCase()} server group $description.serverGroupName in $description.region."
    return null
  }

  Task getTask() {
    TaskRepository.threadLocalTask.get()
  }
}
