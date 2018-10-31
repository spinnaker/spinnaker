/*
 * Copyright (c) 2017 Oracle America, Inc.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */
package com.netflix.spinnaker.clouddriver.oracle.deploy.op

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.oracle.deploy.description.EnableDisableOracleServerGroupDescription
import com.netflix.spinnaker.clouddriver.oracle.service.servergroup.OracleServerGroupService
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import org.springframework.beans.factory.annotation.Autowired

abstract class AbstractEnableDisableAtomicOperation implements AtomicOperation<Void> {

  abstract boolean isDisable()

  abstract String getPhaseName()

  EnableDisableOracleServerGroupDescription description

  @Autowired
  OracleServerGroupService oracleServerGroupService

  @Autowired
  ObjectMapper objectMapper

  AbstractEnableDisableAtomicOperation(EnableDisableOracleServerGroupDescription description) {
    this.description = description
  }

  @Override
  Void operate(List priorOutputs) {
    String verb = disable ? 'disable' : 'enable'
    String presentParticipling = disable ? 'Disabling' : 'Enabling'

    task.updateStatus phaseName, "Initializing $verb server group operation for $description.serverGroupName in " +
      "$description.region..."
    def serverGroup = oracleServerGroupService.getServerGroup(description.credentials, description.application, description.serverGroupName)
    //TODO stop all instances in sg
    if (disable) {
      task.updateStatus phaseName, "$presentParticipling server group from Http(s) load balancers..."
      oracleServerGroupService.disableServerGroup(task, description.credentials, description.serverGroupName)
    } else {
      task.updateStatus phaseName, "Registering server group with Http(s) load balancers..."
      oracleServerGroupService.enableServerGroup(task, description.credentials, description.serverGroupName)
    }

    task.updateStatus phaseName, "$presentParticipling server group $description.serverGroupName in $description.region..."
    task.updateStatus phaseName, "Done ${presentParticipling.toLowerCase()} server group $description.serverGroupName in $description.region."
    return null
  }

  Task getTask() {
    TaskRepository.threadLocalTask.get()
  }
}
