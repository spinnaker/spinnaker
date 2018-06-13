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
import com.netflix.spinnaker.clouddriver.oracle.deploy.description.DestroyOracleServerGroupDescription
import com.netflix.spinnaker.clouddriver.oracle.model.OracleServerGroup
import com.netflix.spinnaker.clouddriver.oracle.security.OracleNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.oracle.service.servergroup.OracleServerGroupService
import com.oracle.bmc.loadbalancer.LoadBalancerClient
import com.oracle.bmc.loadbalancer.responses.DeleteBackendSetResponse
import spock.lang.Specification

class DestroyOracleServerGroupAtomicOperationSpec extends Specification {

  def "Triggers destroying of a server group"() {
    setup:
    def destroyDesc = new DestroyOracleServerGroupDescription()
    destroyDesc.serverGroupName = "sg1"
    def creds = Mock(OracleNamedAccountCredentials)
    def loadBalancerClient = Mock(LoadBalancerClient)
    creds.loadBalancerClient >> loadBalancerClient
    destroyDesc.credentials = creds
    TaskRepository.threadLocalTask.set(Mock(Task))
    def sgService = Mock(OracleServerGroupService)
    DestroyOracleServerGroupAtomicOperation op = new DestroyOracleServerGroupAtomicOperation(destroyDesc)
    op.oracleServerGroupService = sgService
    GroovySpy(OracleWorkRequestPoller, global: true)


    when:
    op.operate(null)

    then:
    1 * sgService.destroyServerGroup(_, _, "sg1")
    1 * sgService.getServerGroup(_, _, "sg1") >> new OracleServerGroup(loadBalancerId: "ocid.lb.oc1..12345")
    1 * loadBalancerClient.deleteBackendSet(_) >> DeleteBackendSetResponse.builder().opcWorkRequestId("wr1").build()
    1 * OracleWorkRequestPoller.poll("wr1", _, _, loadBalancerClient) >> null
  }
}
