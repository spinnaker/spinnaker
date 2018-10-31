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
import com.netflix.spinnaker.clouddriver.oracle.deploy.OracleWorkRequestPoller
import com.netflix.spinnaker.clouddriver.oracle.deploy.description.EnableDisableOracleServerGroupDescription
import com.netflix.spinnaker.clouddriver.oracle.model.OracleServerGroup
import com.netflix.spinnaker.clouddriver.oracle.security.OracleNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.oracle.service.servergroup.OracleServerGroupService
import com.oracle.bmc.loadbalancer.LoadBalancerClient
import com.oracle.bmc.loadbalancer.model.Listener
import com.oracle.bmc.loadbalancer.model.LoadBalancer
import com.oracle.bmc.loadbalancer.responses.GetLoadBalancerResponse
import com.oracle.bmc.loadbalancer.responses.UpdateListenerResponse
import spock.lang.Specification

class EnableOracleServerGroupAtomicOperationSpec extends Specification {

  def "Triggers enabling of a server group"() {
    setup:
    def enableDesc = new EnableDisableOracleServerGroupDescription()
    enableDesc.serverGroupName = "sg1"
    def creds = Mock(OracleNamedAccountCredentials)
    def loadBalancerClient = Mock(LoadBalancerClient)
    creds.loadBalancerClient >> loadBalancerClient
    enableDesc.credentials = creds
    GroovySpy(OracleWorkRequestPoller, global: true)

    TaskRepository.threadLocalTask.set(Mock(Task))
    def sgService = Mock(OracleServerGroupService)
    EnableOracleServerGroupAtomicOperation op = new EnableOracleServerGroupAtomicOperation(enableDesc)
    op.oracleServerGroupService = sgService
    op.objectMapper = new ObjectMapper()

    when:
    op.operate(null)

    then:
    1 * sgService.enableServerGroup(_, _, "sg1")
  }
}
