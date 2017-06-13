/*
 * Copyright (c) 2017 Oracle America, Inc.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */
package com.netflix.spinnaker.clouddriver.oraclebmcs.deploy.handler

import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.deploy.DeployDescription
import com.netflix.spinnaker.clouddriver.model.ServerGroup
import com.netflix.spinnaker.clouddriver.oraclebmcs.deploy.OracleBMCSWorkRequestPoller
import com.netflix.spinnaker.clouddriver.oraclebmcs.deploy.description.BasicOracleBMCSDeployDescription
import com.netflix.spinnaker.clouddriver.oraclebmcs.model.OracleBMCSServerGroup
import com.netflix.spinnaker.clouddriver.oraclebmcs.provider.view.OracleBMCSClusterProvider
import com.netflix.spinnaker.clouddriver.oraclebmcs.security.OracleBMCSNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.oraclebmcs.service.servergroup.OracleBMCSServerGroupService
import com.oracle.bmc.core.ComputeClient
import com.oracle.bmc.core.VirtualNetworkClient
import com.oracle.bmc.loadbalancer.LoadBalancerClient
import com.oracle.bmc.loadbalancer.model.BackendSet
import com.oracle.bmc.loadbalancer.model.HealthChecker
import com.oracle.bmc.loadbalancer.model.Listener
import com.oracle.bmc.loadbalancer.model.LoadBalancer
import com.oracle.bmc.loadbalancer.responses.CreateBackendSetResponse
import com.oracle.bmc.loadbalancer.responses.GetLoadBalancerResponse
import com.oracle.bmc.loadbalancer.responses.UpdateListenerResponse
import spock.lang.Specification

class BasicOracleBMCSDeployHandlerSpec extends Specification {

  def "Handles correct description"(def desc, def result) {
    setup:
    def handler = new BasicOracleBMCSDeployHandler()

    expect:
    handler.handles(desc) == result

    where:
    desc                                   || result
    new BasicOracleBMCSDeployDescription() || true
    Mock(DeployDescription)                || false
  }

  def "Create server group"() {
    setup:
    def creds = Mock(OracleBMCSNamedAccountCredentials)
    creds.compartmentId >> "ocid.compartment.123"
    TaskRepository.threadLocalTask.set(Mock(Task))
    def desc = new BasicOracleBMCSDeployDescription()
    desc.credentials = creds
    desc.capacity = new ServerGroup.Capacity(desired: 3)
    desc.application = "foo"
    desc.stack = "dev"
    desc.region = "us-phoenix-1"
    desc.loadBalancerId = "ocid.lb.oc1..1918273"
    def loadBalancerClient = Mock(LoadBalancerClient)
    creds.loadBalancerClient >> loadBalancerClient
    def computeClient = Mock(ComputeClient)
    creds.computeClient >> computeClient
    def networkClient = Mock(VirtualNetworkClient)
    creds.networkClient >> networkClient
    GroovySpy(OracleBMCSWorkRequestPoller, global: true)
    def sgService = Mock(OracleBMCSServerGroupService)
    def sg = new OracleBMCSServerGroup(launchConfig: ["createdTime": System.currentTimeMillis()])
    def handler = new BasicOracleBMCSDeployHandler()
    handler.oracleBMCSServerGroupService = sgService
    def sgProvider = Mock(OracleBMCSClusterProvider)
    handler.clusterProvider = sgProvider
    def sgViewMock = Mock(ServerGroup)
    def instanceCounts = new ServerGroup.InstanceCounts()
    instanceCounts.setUp(3)
    instanceCounts.setTotal(3)

    when:
    def res = handler.handle(desc, null)

    then:
    1 * sgService.listServerGroupNamesByClusterName(_, "foo-dev") >> ["foo-dev-v001"]
    1 * sgService.getServerGroup(creds, "foo", "foo-dev-v001") >> sg
    1 * sgService.createServerGroup(_)
    res != null
    res.serverGroupNames == ["us-phoenix-1:foo-dev-v002"]
    1 * loadBalancerClient.getLoadBalancer(_) >> GetLoadBalancerResponse.builder()
      .loadBalancer(LoadBalancer.builder()
      .listeners(["foo-dev": Listener.builder()
      .defaultBackendSetName("foo-dev-v001").build()])
      .backendSets(["foo-dev-template": BackendSet.builder()
      .healthChecker(HealthChecker.builder().build())
      .build()]).build()).build()
    1 * loadBalancerClient.createBackendSet(_) >> CreateBackendSetResponse.builder().build()
    1 * loadBalancerClient.updateListener(_) >> UpdateListenerResponse.builder().opcWorkRequestId("wr1").build()
    2 * OracleBMCSWorkRequestPoller.poll(_, _, _, loadBalancerClient) >> null
    1 * sgProvider.getServerGroup(_, _, _) >> sgViewMock
    sgViewMock.instanceCounts >> instanceCounts
  }
}
