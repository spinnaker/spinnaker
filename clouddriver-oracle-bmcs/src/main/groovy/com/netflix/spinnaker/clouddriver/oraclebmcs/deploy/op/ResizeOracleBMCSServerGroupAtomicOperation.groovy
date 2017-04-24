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
import com.netflix.spinnaker.clouddriver.oraclebmcs.deploy.description.ResizeOracleBMCSServerGroupDescription
import com.netflix.spinnaker.clouddriver.oraclebmcs.service.servergroup.OracleBMCSServerGroupService
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import org.springframework.beans.factory.annotation.Autowired

class ResizeOracleBMCSServerGroupAtomicOperation implements AtomicOperation<Void> {

  private final ResizeOracleBMCSServerGroupDescription description

  private static final String BASE_PHASE = "RESIZE_SERVER_GROUP"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  @Autowired
  OracleBMCSServerGroupService oracleBMCSServerGroupService

  ResizeOracleBMCSServerGroupAtomicOperation(ResizeOracleBMCSServerGroupDescription description) {
    this.description = description
  }

  @Override
  Void operate(List priorOutputs) {
    task.updateStatus BASE_PHASE, "Resizing server group: " + description.serverGroupName
    oracleBMCSServerGroupService.resizeServerGroup(task, description.credentials, description.serverGroupName, description.capacity.desired)
    task.updateStatus BASE_PHASE, "Completed server group resize"
    return null
  }
}
