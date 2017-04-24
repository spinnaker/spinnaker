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
import com.netflix.spinnaker.clouddriver.model.ServerGroup
import com.netflix.spinnaker.clouddriver.oraclebmcs.deploy.description.ResizeOracleBMCSServerGroupDescription
import com.netflix.spinnaker.clouddriver.oraclebmcs.service.servergroup.OracleBMCSServerGroupService
import spock.lang.Specification

class ResizeOracleBMCSServerGroupAtomicOperationSpec extends Specification {

  def "Resize server group"() {
    setup:
    def resizeDesc = new ResizeOracleBMCSServerGroupDescription()
    resizeDesc.serverGroupName = "sg1"
    resizeDesc.capacity = new ServerGroup.Capacity(desired: 3)

    TaskRepository.threadLocalTask.set(Mock(Task))
    def sgService = Mock(OracleBMCSServerGroupService)
    ResizeOracleBMCSServerGroupAtomicOperation op = new ResizeOracleBMCSServerGroupAtomicOperation(resizeDesc)
    op.oracleBMCSServerGroupService = sgService

    when:
    op.operate(null)

    then:
    1 * sgService.resizeServerGroup(_, _, "sg1", 3)
  }
}
