/*
 * Copyright (c) 2017 Oracle America, Inc.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */
package com.netflix.spinnaker.clouddriver.oracle.deploy.op

import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.oracle.deploy.OracleWorkRequestPoller
import com.netflix.spinnaker.clouddriver.oracle.deploy.description.DeleteLoadBalancerDescription
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.oracle.bmc.loadbalancer.model.BackendSetDetails
import com.oracle.bmc.loadbalancer.model.CreateLoadBalancerDetails
import com.oracle.bmc.loadbalancer.model.HealthCheckerDetails
import com.oracle.bmc.loadbalancer.model.ListenerDetails
import com.oracle.bmc.loadbalancer.requests.DeleteLoadBalancerRequest
import groovy.util.logging.Slf4j

@Slf4j
class DeleteOracleLoadBalancerAtomicOperation implements AtomicOperation<Void> {

  private final DeleteLoadBalancerDescription description

  private static final String BASE_PHASE = "DELETE_LOADBALANCER"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  DeleteOracleLoadBalancerAtomicOperation(DeleteLoadBalancerDescription description) {
    this.description = description
  }

  @Override
  Void operate(List priorOutputs) {
    def task = getTask()
    task.updateStatus(BASE_PHASE, "Delete LoadBalancer: ${description}")
    def request = DeleteLoadBalancerRequest.builder().loadBalancerId(description.loadBalancerId).build()
    def rs = description.credentials.loadBalancerClient.deleteLoadBalancer(request)
    task.updateStatus(BASE_PHASE, "Delete LoadBalancer request submitted - work request id: ${rs.getOpcWorkRequestId()}")
    OracleWorkRequestPoller.poll(rs.getOpcWorkRequestId(), BASE_PHASE, task, description.credentials.loadBalancerClient)
    return null
  }
}
