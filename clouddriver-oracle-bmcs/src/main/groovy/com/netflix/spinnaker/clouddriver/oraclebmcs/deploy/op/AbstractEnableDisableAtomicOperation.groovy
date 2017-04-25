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
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.oraclebmcs.deploy.description.EnableDisableOracleBMCSServerGroupDescription
import com.netflix.spinnaker.clouddriver.oraclebmcs.service.servergroup.OracleBMCSServerGroupService
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
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

    if (disable) {
      task.updateStatus phaseName, "$presentParticipling server group from Http(s) load balancers..."

      oracleBMCSServerGroupService.disableServerGroup(task, description.credentials, description.serverGroupName)

    } else {
      task.updateStatus phaseName, "Registering server group with Http(s) load balancers..."

      oracleBMCSServerGroupService.enableServerGroup(task, description.credentials, description.serverGroupName)
    }

    task.updateStatus phaseName, "$presentParticipling server group $description.serverGroupName in $description.region..."
    task.updateStatus phaseName, "Done ${presentParticipling.toLowerCase()} server group $description.serverGroupName in $description.region."
    return null
  }

  Task getTask() {
    TaskRepository.threadLocalTask.get()
  }
}
