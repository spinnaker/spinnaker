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
import com.netflix.spinnaker.clouddriver.oraclebmcs.deploy.OracleBMCSWorkRequestPoller
import com.netflix.spinnaker.clouddriver.oraclebmcs.deploy.description.EnableDisableOracleBMCSServerGroupDescription
import com.netflix.spinnaker.clouddriver.oraclebmcs.model.OracleBMCSServerGroup
import com.netflix.spinnaker.clouddriver.oraclebmcs.security.OracleBMCSNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.oraclebmcs.service.servergroup.OracleBMCSServerGroupService
import com.oracle.bmc.loadbalancer.LoadBalancerClient
import com.oracle.bmc.loadbalancer.model.Listener
import com.oracle.bmc.loadbalancer.model.LoadBalancer
import com.oracle.bmc.loadbalancer.responses.GetLoadBalancerResponse
import com.oracle.bmc.loadbalancer.responses.UpdateListenerResponse
import spock.lang.Specification

class EnableOracleBMCSServerGroupAtomicOperationSpec extends Specification {

  def "Triggers enabling of a server group"() {
    setup:
    def enableDesc = new EnableDisableOracleBMCSServerGroupDescription()
    enableDesc.serverGroupName = "sg1"
    def creds = Mock(OracleBMCSNamedAccountCredentials)
    def loadBalancerClient = Mock(LoadBalancerClient)
    creds.loadBalancerClient >> loadBalancerClient
    enableDesc.credentials = creds
    GroovySpy(OracleBMCSWorkRequestPoller, global: true)

    TaskRepository.threadLocalTask.set(Mock(Task))
    def sgService = Mock(OracleBMCSServerGroupService)
    EnableOracleBMCSServerGroupAtomicOperation op = new EnableOracleBMCSServerGroupAtomicOperation(enableDesc)
    op.oracleBMCSServerGroupService = sgService
    op.objectMapper = new ObjectMapper()

    when:
    op.operate(null)

    then:
    1 * sgService.enableServerGroup(_, _, "sg1")
    1 * sgService.getServerGroup(_, _, "sg1") >> new OracleBMCSServerGroup(loadBalancerId: "ocid.lb.oc1..12345")
    1 * loadBalancerClient.getLoadBalancer(_) >> GetLoadBalancerResponse.builder()
      .loadBalancer(LoadBalancer.builder()
      .listeners(["sg1": Listener.builder()
      .defaultBackendSetName("sg1-old").build()]).build()).build()
    1 * loadBalancerClient.updateListener(_) >> UpdateListenerResponse.builder().opcWorkRequestId("wr1").build()
    1 * OracleBMCSWorkRequestPoller.poll("wr1", _, _, loadBalancerClient) >> null
  }
}
