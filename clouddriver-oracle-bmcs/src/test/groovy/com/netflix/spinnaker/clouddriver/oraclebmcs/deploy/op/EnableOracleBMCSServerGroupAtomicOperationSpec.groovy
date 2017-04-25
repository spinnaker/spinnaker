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
import spock.lang.Specification

class EnableOracleBMCSServerGroupAtomicOperationSpec extends Specification {

  def "Triggers enabling of a server group"() {
    setup:
    def enableDesc = new EnableDisableOracleBMCSServerGroupDescription()
    enableDesc.serverGroupName = "sg1"

    TaskRepository.threadLocalTask.set(Mock(Task))
    def sgService = Mock(OracleBMCSServerGroupService)
    EnableOracleBMCSServerGroupAtomicOperation op = new EnableOracleBMCSServerGroupAtomicOperation(enableDesc)
    op.oracleBMCSServerGroupService = sgService
    op.objectMapper = new ObjectMapper()

    when:
    op.operate(null)

    then:
    1 * sgService.enableServerGroup(_, _, "sg1")
  }
}
