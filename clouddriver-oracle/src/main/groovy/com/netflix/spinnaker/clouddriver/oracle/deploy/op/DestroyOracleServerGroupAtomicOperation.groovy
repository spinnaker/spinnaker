/*
 * Copyright (c) 2017 Oracle America, Inc.
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
import com.netflix.spinnaker.clouddriver.oracle.deploy.OracleWorkRequestPoller
import com.netflix.spinnaker.clouddriver.oracle.deploy.description.DestroyOracleServerGroupDescription
import com.netflix.spinnaker.clouddriver.oracle.service.servergroup.OracleServerGroupService
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.oracle.bmc.loadbalancer.requests.DeleteBackendSetRequest
import com.oracle.bmc.loadbalancer.requests.GetBackendSetRequest
import com.oracle.bmc.model.BmcException
import org.springframework.beans.factory.annotation.Autowired

class DestroyOracleServerGroupAtomicOperation implements AtomicOperation<Void> {

  private final DestroyOracleServerGroupDescription description

  private static final String BASE_PHASE = "DESTROY_SERVER_GROUP"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  @Autowired
  OracleServerGroupService oracleServerGroupService

  DestroyOracleServerGroupAtomicOperation(DestroyOracleServerGroupDescription description) {
    this.description = description
  }

  @Override
  Void operate(List priorOutputs) {
    def sg = oracleServerGroupService.getServerGroup(description.credentials, Names.parseName(description.serverGroupName).app, description.serverGroupName)
    if (sg.loadBalancerId) {
      task.updateStatus BASE_PHASE, "Destroying server group backend set: " + description.serverGroupName
      try {
        description.credentials.loadBalancerClient.getBackendSet(GetBackendSetRequest.builder()
          .backendSetName(description.serverGroupName)
          .loadBalancerId(sg.loadBalancerId)
          .build())
        def workResponse = description.credentials.loadBalancerClient.deleteBackendSet(DeleteBackendSetRequest.builder()
          .backendSetName(description.serverGroupName)
          .loadBalancerId(sg.loadBalancerId)
          .build())
        OracleWorkRequestPoller.poll(workResponse.opcWorkRequestId, BASE_PHASE, task, description.credentials.loadBalancerClient)
      } catch (BmcException e) {
        if (e.statusCode == 404) {
          task.updateStatus BASE_PHASE, "Backend set did not exist...continuing"
        } else {
          throw e
        }
      }
    }

    task.updateStatus BASE_PHASE, "Destroying server group: " + description.serverGroupName
    oracleServerGroupService.destroyServerGroup(task, description.credentials, description.serverGroupName)

    task.updateStatus BASE_PHASE, "Completed server group destruction"
    return null
  }
}
