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
import com.netflix.spinnaker.clouddriver.oraclebmcs.deploy.OracleBMCSWorkRequestPoller
import com.netflix.spinnaker.clouddriver.oraclebmcs.deploy.description.ResizeOracleBMCSServerGroupDescription
import com.netflix.spinnaker.clouddriver.oraclebmcs.model.OracleBMCSServerGroup
import com.netflix.spinnaker.clouddriver.oraclebmcs.provider.view.OracleBMCSClusterProvider
import com.netflix.spinnaker.clouddriver.oraclebmcs.security.OracleBMCSNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.oraclebmcs.service.servergroup.OracleBMCSServerGroupService
import com.oracle.bmc.core.ComputeClient
import com.oracle.bmc.core.VirtualNetworkClient
import com.oracle.bmc.loadbalancer.LoadBalancerClient
import com.oracle.bmc.loadbalancer.model.BackendSet
import com.oracle.bmc.loadbalancer.model.HealthChecker
import com.oracle.bmc.loadbalancer.model.LoadBalancer
import com.oracle.bmc.loadbalancer.responses.GetLoadBalancerResponse
import com.oracle.bmc.loadbalancer.responses.UpdateBackendSetResponse
import spock.lang.Specification

class ResizeOracleBMCSServerGroupAtomicOperationSpec extends Specification {

  def "Resize server group"() {
    setup:
    def resizeDesc = new ResizeOracleBMCSServerGroupDescription()
    resizeDesc.serverGroupName = "sg1"
    resizeDesc.capacity = new ServerGroup.Capacity(desired: 3)
    def creds = Mock(OracleBMCSNamedAccountCredentials)
    def loadBalancerClient = Mock(LoadBalancerClient)
    creds.loadBalancerClient >> loadBalancerClient
    def computeClient = Mock(ComputeClient)
    creds.computeClient >> computeClient
    def networkClient = Mock(VirtualNetworkClient)
    creds.networkClient >> networkClient
    resizeDesc.credentials = creds
    GroovySpy(OracleBMCSWorkRequestPoller, global: true)

    TaskRepository.threadLocalTask.set(Mock(Task))
    def sgService = Mock(OracleBMCSServerGroupService)
    ResizeOracleBMCSServerGroupAtomicOperation op = new ResizeOracleBMCSServerGroupAtomicOperation(resizeDesc)
    op.oracleBMCSServerGroupService = sgService


    def sgProvider = Mock(OracleBMCSClusterProvider)
    op.clusterProvider = sgProvider
    def sgViewMock = Mock(ServerGroup)
    def instanceCounts = new ServerGroup.InstanceCounts()
    instanceCounts.setUp(3)
    instanceCounts.setTotal(3)

    when:
    op.operate(null)

    then:
    1 * sgService.resizeServerGroup(_, _, "sg1", 3)
    1 * sgService.getServerGroup(_, _, "sg1") >> new OracleBMCSServerGroup(loadBalancerId: "ocid.lb.oc1..12345", name: "sg1", credentials: creds)
    1 * loadBalancerClient.getLoadBalancer(_) >> GetLoadBalancerResponse.builder()
      .loadBalancer(LoadBalancer.builder()
      .backendSets(["sg1-template": BackendSet.builder()
      .healthChecker(HealthChecker.builder().build())
      .build()]).build()).build()
    1 * loadBalancerClient.updateBackendSet(_) >> UpdateBackendSetResponse.builder().opcWorkRequestId("wr1").build()
    1 * OracleBMCSWorkRequestPoller.poll("wr1", _, _, loadBalancerClient) >> null
    1 * sgProvider.getServerGroup(_, _, _) >> sgViewMock
    sgViewMock.instanceCounts >> instanceCounts
  }
}
