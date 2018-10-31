/*
 * Copyright (c) 2017, 2018, Oracle Corporation and/or its affiliates. All rights reserved.
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
import com.netflix.spinnaker.clouddriver.oracle.model.OracleInstance
import com.netflix.spinnaker.clouddriver.oracle.model.OracleServerGroup
import com.netflix.spinnaker.clouddriver.oracle.security.OracleNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.oracle.service.servergroup.OracleServerGroupService
import com.oracle.bmc.loadbalancer.LoadBalancerClient
import com.oracle.bmc.loadbalancer.model.*
import com.oracle.bmc.loadbalancer.requests.UpdateBackendSetRequest
import com.oracle.bmc.loadbalancer.responses.GetLoadBalancerResponse
import com.oracle.bmc.loadbalancer.responses.UpdateBackendSetResponse
import groovy.ui.SystemOutputInterceptor
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
    def backends = ['10.1.20.1', '10.1.20.2', '10.1.20.3','10.1.20.4']
    def srvGroup = ['10.1.20.2', '10.1.20.4'] //to be destroyed

    when:
    op.operate(null)

    then:
    1 * loadBalancerClient.getLoadBalancer(_) >> GetLoadBalancerResponse.builder().loadBalancer(
      LoadBalancer.builder().backendSets(['myBackendSet': BackendSet.builder().backends(
        backends.collect { Backend.builder().ipAddress(it).build() } ).build()]).build()).build()
    1 * sgService.getServerGroup(_, _, "sg1") >> 
      new OracleServerGroup(loadBalancerId: "ocid.lb.oc1..12345", backendSetName: 'myBackendSet', 
        instances: srvGroup.collect {new OracleInstance(id: it, privateIp: it)} as Set)
    1 * sgService.destroyServerGroup(_, _, "sg1")
    1 * loadBalancerClient.updateBackendSet(_) >> { args ->
      UpdateBackendSetRequest req = (UpdateBackendSetRequest) args[0]
      def updatedBackendSet = req.updateBackendSetDetails.backends.collect {it.ipAddress}
      assert updatedBackendSet.size() == 2
      assert updatedBackendSet.contains('10.1.20.1')
      assert updatedBackendSet.contains('10.1.20.3')
      UpdateBackendSetResponse.builder().opcWorkRequestId("wr1").build()
    }
    1 * OracleWorkRequestPoller.poll("wr1", _, _, loadBalancerClient) >> null
  }
}
