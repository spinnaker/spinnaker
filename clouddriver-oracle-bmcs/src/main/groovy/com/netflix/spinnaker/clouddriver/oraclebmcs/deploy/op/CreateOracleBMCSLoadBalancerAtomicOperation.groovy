/*
 * Copyright (c) 2017 Oracle America, Inc.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */
package com.netflix.spinnaker.clouddriver.oraclebmcs.deploy.op

import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.oraclebmcs.deploy.OracleBMCSWorkRequestPoller
import com.netflix.spinnaker.clouddriver.oraclebmcs.deploy.description.CreateLoadBalancerDescription
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.oracle.bmc.loadbalancer.model.BackendSetDetails
import com.oracle.bmc.loadbalancer.model.CreateLoadBalancerDetails
import com.oracle.bmc.loadbalancer.model.HealthCheckerDetails
import com.oracle.bmc.loadbalancer.model.ListenerDetails
import com.oracle.bmc.loadbalancer.requests.CreateLoadBalancerRequest
import groovy.util.logging.Slf4j

@Slf4j
class CreateOracleBMCSLoadBalancerAtomicOperation implements AtomicOperation<Void> {

  private final CreateLoadBalancerDescription description

  private static final String BASE_PHASE = "CREATE_LOADBALANCER"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  CreateOracleBMCSLoadBalancerAtomicOperation(CreateLoadBalancerDescription description) {
    this.description = description
  }

  @Override
  Void operate(List priorOutputs) {
    def task = getTask()
    def clusterName = "${description.application}${description.stack ? '-' + description.stack : ''}"
    def backendSetTemplateName = "$clusterName-template"
    def dummyBackendSet = BackendSetDetails.builder()
      .policy(description.policy)
      .healthChecker(HealthCheckerDetails.builder()
      .protocol(description.healthCheck.protocol)
      .port(description.healthCheck.port)
      .intervalInMillis(description.healthCheck.interval)
      .retries(description.healthCheck.retries)
      .timeoutInMillis(description.healthCheck.timeout)
      .urlPath(description.healthCheck.url)
      .returnCode(description.healthCheck.statusCode)
      .responseBodyRegex(description.healthCheck.responseBodyRegex)
      .build())
      .build()

    def rq = CreateLoadBalancerRequest.builder()
      .createLoadBalancerDetails(CreateLoadBalancerDetails.builder()
      .displayName(clusterName)
      .compartmentId(description.credentials.compartmentId)
      .shapeName(description.shape)
      .subnetIds(description.subnetIds)
      .backendSets([(backendSetTemplateName.toString()): dummyBackendSet])
      .listeners([(clusterName.toString()): ListenerDetails.builder()
      .port(description.listener.port)
      .protocol(description.listener.protocol)
      .defaultBackendSetName(backendSetTemplateName)
      .build()])
      .build()).build()

    def rs = description.credentials.loadBalancerClient.createLoadBalancer(rq)
    task.updateStatus(BASE_PHASE, "Create LB rq submitted - work request id: ${rs.getOpcWorkRequestId()}")

    OracleBMCSWorkRequestPoller.poll(rs.getOpcWorkRequestId(), BASE_PHASE, task, description.credentials.loadBalancerClient)

    return null
  }
}
