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
import com.netflix.spinnaker.clouddriver.model.ServerGroup
import com.netflix.spinnaker.clouddriver.oracle.deploy.OracleWorkRequestPoller
import com.netflix.spinnaker.clouddriver.oracle.deploy.description.ResizeOracleServerGroupDescription
import com.netflix.spinnaker.clouddriver.oracle.model.OracleInstance
import com.netflix.spinnaker.clouddriver.oracle.model.OracleServerGroup
import com.netflix.spinnaker.clouddriver.oracle.provider.view.OracleClusterProvider
import com.netflix.spinnaker.clouddriver.oracle.security.OracleNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.oracle.service.servergroup.OracleServerGroupService
import com.oracle.bmc.core.ComputeClient
import com.oracle.bmc.core.VirtualNetworkClient
import com.oracle.bmc.loadbalancer.LoadBalancerClient
import com.oracle.bmc.loadbalancer.model.Backend
import com.oracle.bmc.loadbalancer.model.BackendSet
import com.oracle.bmc.loadbalancer.model.HealthChecker
import com.oracle.bmc.loadbalancer.model.LoadBalancer
import com.oracle.bmc.loadbalancer.requests.UpdateBackendSetRequest
import com.oracle.bmc.loadbalancer.responses.GetLoadBalancerResponse
import com.oracle.bmc.loadbalancer.responses.UpdateBackendSetResponse
import spock.lang.Specification

class ResizeOracleServerGroupAtomicOperationSpec extends Specification {

  def "resize up serverGroup from 2 to 4"() {
    setup:
    int targetSize = 4
    def resizeDesc = resize('sg1', targetSize)
    def creds = resizeDesc.credentials
    def loadBalancerClient = creds.loadBalancerClient
    TaskRepository.threadLocalTask.set(Mock(Task))
    def sgService = Mock(OracleServerGroupService)
    ResizeOracleServerGroupAtomicOperation op = new ResizeOracleServerGroupAtomicOperation(resizeDesc)
    op.oracleServerGroupService = sgService
    def sgProvider = Mock(OracleClusterProvider)
    op.clusterProvider = sgProvider
    def sgViewMock = Mock(ServerGroup)
    def instanceCounts = new ServerGroup.InstanceCounts()
    instanceCounts.setUp(targetSize)
    instanceCounts.setTotal(targetSize)
    def backends = ['10.1.20.1', '10.1.20.2', '10.1.20.3','10.1.20.4']
    def srvGroup = ['10.1.20.2', '10.1.20.4'] 
    def newGroup = ['10.1.20.2', '10.1.20.4', '10.1.20.5', '10.1.20.6'] 

    when:
    op.operate(null)

    then:
    1 * sgService.resizeServerGroup(_, _, "sg1", targetSize)
    2 * sgService.getServerGroup(_, _, "sg1") >> 
      new OracleServerGroup(loadBalancerId: "ocid.lb.oc1..12345", backendSetName: 'sg1BackendSet', name: "sg1", credentials: creds,
        instances: srvGroup.collect {new OracleInstance(id: it, privateIp: it)} as Set) >>
      new OracleServerGroup(loadBalancerId: "ocid.lb.oc1..12345", backendSetName: 'sg1BackendSet', name: "sg1", credentials: creds,
        instances: newGroup.collect {new OracleInstance(id: it, privateIp: it)} as Set)
    1 * loadBalancerClient.getLoadBalancer(_) >> GetLoadBalancerResponse.builder()
      .loadBalancer(LoadBalancer.builder()
      .backendSets(["sg1BackendSet": BackendSet.builder()
      .healthChecker(HealthChecker.builder().build())
      .backends( backends.collect { Backend.builder().ipAddress(it).build() } )
      .build()]).build()).build()
    1 * loadBalancerClient.updateBackendSet(_) >> { args ->
      UpdateBackendSetRequest req = (UpdateBackendSetRequest) args[0]
      def updatedBackendSet = req.updateBackendSetDetails.backends.collect {it.ipAddress}
      assert updatedBackendSet.size() == 6
      assert updatedBackendSet.contains('10.1.20.1')
      assert updatedBackendSet.contains('10.1.20.2')
      assert updatedBackendSet.contains('10.1.20.5')
      assert updatedBackendSet.contains('10.1.20.6')
      UpdateBackendSetResponse.builder().opcWorkRequestId("wr1").build()
    }
    1 * OracleWorkRequestPoller.poll("wr1", _, _, loadBalancerClient) >> null
    1 * sgProvider.getServerGroup(_, _, _) >> sgViewMock
    sgViewMock.instanceCounts >> instanceCounts
  }

  def "resize down serverGroup from 3 to 1"() {
    setup:
    def sgName = 'sgDown'
    def resizeDesc = resize(sgName, 1)
    def creds = resizeDesc.credentials
    def loadBalancerClient = creds.loadBalancerClient
    TaskRepository.threadLocalTask.set(Mock(Task))
    def sgService = Mock(OracleServerGroupService)
    ResizeOracleServerGroupAtomicOperation op = new ResizeOracleServerGroupAtomicOperation(resizeDesc)
    op.oracleServerGroupService = sgService
    def sgProvider = Mock(OracleClusterProvider)
    op.clusterProvider = sgProvider
    def sgViewMock = Mock(ServerGroup)
    def instanceCounts = new ServerGroup.InstanceCounts()
    instanceCounts.setUp(1)
    instanceCounts.setTotal(1)
    def backends = ['10.1.20.1', '10.1.20.2', '10.1.20.3','10.1.20.4', '10.1.20.5', '10.1.20.6']
    def srvGroup = ['10.1.20.2', '10.1.20.4', '10.1.20.6'] 
    def newGroup = ['10.1.20.4'] 

    when:
    op.operate(null)

    then:
    1 * sgService.resizeServerGroup(_, _, sgName, 1)
    2 * sgService.getServerGroup(_, _, sgName) >> 
      new OracleServerGroup(loadBalancerId: "ocid.lb.oc1..12345", backendSetName: 'sg1BackendSet', name: sgName, credentials: creds,
        instances: srvGroup.collect {new OracleInstance(id: it, privateIp: it)} as Set) >>
      new OracleServerGroup(loadBalancerId: "ocid.lb.oc1..12345", backendSetName: 'sg1BackendSet', name: sgName, credentials: creds,
        instances: newGroup.collect {new OracleInstance(id: it, privateIp: it)} as Set)
    1 * loadBalancerClient.getLoadBalancer(_) >> GetLoadBalancerResponse.builder()
      .loadBalancer(LoadBalancer.builder()
      .backendSets(["sg1BackendSet": BackendSet.builder()
      .healthChecker(HealthChecker.builder().build())
      .backends( backends.collect { Backend.builder().ipAddress(it).build() } )
      .build()]).build()).build()
    1 * loadBalancerClient.updateBackendSet(_) >> { args ->
      UpdateBackendSetRequest req = (UpdateBackendSetRequest) args[0]
      def updatedBackendSet = req.updateBackendSetDetails.backends.collect {it.ipAddress}
      assert updatedBackendSet.size() == 4
      assert updatedBackendSet.contains('10.1.20.1')
      assert updatedBackendSet.contains('10.1.20.3')
      assert updatedBackendSet.contains('10.1.20.5')
      assert updatedBackendSet.contains('10.1.20.4')
      UpdateBackendSetResponse.builder().opcWorkRequestId("wr1").build()
    }
    1 * OracleWorkRequestPoller.poll("wr1", _, _, loadBalancerClient) >> null
    1 * sgProvider.getServerGroup(_, _, _) >> sgViewMock
    sgViewMock.instanceCounts >> instanceCounts
  }
  
  def 'resize same size serverGroup'() {
    setup:
    int targetSize = 2
    def resizeDesc = resize('sgSame', targetSize)
    def creds = resizeDesc.credentials
    def loadBalancerClient = creds.loadBalancerClient

    TaskRepository.threadLocalTask.set(Mock(Task))
    def sgService = Mock(OracleServerGroupService)
    ResizeOracleServerGroupAtomicOperation op = new ResizeOracleServerGroupAtomicOperation(resizeDesc)
    op.oracleServerGroupService = sgService
    def sgProvider = Mock(OracleClusterProvider)
    op.clusterProvider = sgProvider
    def sgViewMock = Mock(ServerGroup)
    def instanceCounts = new ServerGroup.InstanceCounts()
    instanceCounts.setUp(targetSize)
    instanceCounts.setTotal(targetSize)
    def srvGroup = ['10.1.20.2', '10.1.20.4']

    when:
    op.operate(null)

    then:
    0 * sgService.resizeServerGroup(_, _, "sgSame", targetSize)
    1 * sgService.getServerGroup(_, _, "sgSame") >> 
      new OracleServerGroup(loadBalancerId: "ocid.lb.oc1..12345", backendSetName: 'sg1BackendSet', name: "sgSame", credentials: creds,
        instances: srvGroup.collect {new OracleInstance(id: it, privateIp: it)} as Set)
    0 * loadBalancerClient.getLoadBalancer(_) 
    0 * loadBalancerClient.updateBackendSet(_)
    0 * OracleWorkRequestPoller.poll("wr1", _, _, loadBalancerClient) >> null
    0 * sgProvider.getServerGroup(_, _, _) >> sgViewMock
    sgViewMock.instanceCounts >> instanceCounts
  }
  
  ResizeOracleServerGroupDescription resize(String sgName, int targetSize) {
    def resizeDesc = new ResizeOracleServerGroupDescription()
    resizeDesc.serverGroupName = sgName
    resizeDesc.capacity = new ServerGroup.Capacity(desired: targetSize)
    def creds = Mock(OracleNamedAccountCredentials)
    def loadBalancerClient = Mock(LoadBalancerClient)
    creds.loadBalancerClient >> loadBalancerClient
    def computeClient = Mock(ComputeClient)
    creds.computeClient >> computeClient
    def networkClient = Mock(VirtualNetworkClient)
    creds.networkClient >> networkClient
    resizeDesc.credentials = creds
    GroovySpy(OracleWorkRequestPoller, global: true)
    return resizeDesc
  }
}
