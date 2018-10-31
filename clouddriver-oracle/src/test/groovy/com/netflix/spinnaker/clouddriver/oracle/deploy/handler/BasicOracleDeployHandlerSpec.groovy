/*
 * Copyright (c) 2017, 2018, Oracle Corporation and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */
package com.netflix.spinnaker.clouddriver.oracle.deploy.handler

import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.deploy.DeployDescription
import com.netflix.spinnaker.clouddriver.model.ServerGroup
import com.netflix.spinnaker.clouddriver.oracle.deploy.OracleWorkRequestPoller
import com.netflix.spinnaker.clouddriver.oracle.deploy.description.BasicOracleDeployDescription
import com.netflix.spinnaker.clouddriver.oracle.model.OracleServerGroup
import com.netflix.spinnaker.clouddriver.oracle.provider.view.OracleClusterProvider
import com.netflix.spinnaker.clouddriver.oracle.security.OracleNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.oracle.service.servergroup.OracleServerGroupService
import com.oracle.bmc.core.ComputeClient
import com.oracle.bmc.core.VirtualNetworkClient
import com.oracle.bmc.loadbalancer.LoadBalancerClient
import com.oracle.bmc.loadbalancer.model.BackendSet
import com.oracle.bmc.loadbalancer.model.HealthChecker
import com.oracle.bmc.loadbalancer.model.Listener
import com.oracle.bmc.loadbalancer.model.LoadBalancer
import com.oracle.bmc.loadbalancer.responses.CreateBackendSetResponse
import com.oracle.bmc.loadbalancer.responses.GetLoadBalancerResponse
import com.oracle.bmc.loadbalancer.responses.UpdateBackendSetResponse
import spock.lang.Specification

class BasicOracleDeployHandlerSpec extends Specification {

  def "Handles correct description"(def desc, def result) {
    setup:
    def handler = new BasicOracleDeployHandler()

    expect:
    handler.handles(desc) == result

    where:
    desc                                   || result
    new BasicOracleDeployDescription() || true
    Mock(DeployDescription)                || false
  }

  def "Create server group"() {
    setup:
    def SSHKeys = "ssh-rsa ABC a@b"
    def creds = Mock(OracleNamedAccountCredentials)
    creds.compartmentId >> "ocid.compartment.123"
    TaskRepository.threadLocalTask.set(Mock(Task))
    def desc = new BasicOracleDeployDescription()
    desc.credentials = creds
    desc.capacity = new ServerGroup.Capacity(desired: 3)
    desc.application = "foo"
    desc.stack = "dev"
    desc.region = "us-phoenix-1"
    desc.loadBalancerId = "ocid.lb.oc1..1918273"
    desc.sshAuthorizedKeys = SSHKeys
    desc.backendSetName = "myBackendSet"
    def loadBalancerClient = Mock(LoadBalancerClient)
    creds.loadBalancerClient >> loadBalancerClient
    def computeClient = Mock(ComputeClient)
    creds.computeClient >> computeClient
    def networkClient = Mock(VirtualNetworkClient)
    creds.networkClient >> networkClient
    GroovySpy(OracleWorkRequestPoller, global: true)
    def sgService = Mock(OracleServerGroupService)
    def sg = new OracleServerGroup(launchConfig: ["createdTime": System.currentTimeMillis()])
    def handler = new BasicOracleDeployHandler()
    handler.oracleServerGroupService = sgService
    def sgProvider = Mock(OracleClusterProvider)
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
    1 * sgService.createServerGroup(_, _) >> { args ->
      OracleServerGroup sgArgument = (OracleServerGroup) args[1]
      assert sgArgument.launchConfig.get("sshAuthorizedKeys") == SSHKeys
    }
    res != null
    res.serverGroupNames == ["us-phoenix-1:foo-dev-v002"]
    1 * loadBalancerClient.getLoadBalancer(_) >> GetLoadBalancerResponse.builder()
      .loadBalancer(LoadBalancer.builder()
      .listeners(["foo-dev": Listener.builder()
      .defaultBackendSetName("myBackendSet").build()])
      .backendSets(["myBackendSet": BackendSet.builder()
      .healthChecker(HealthChecker.builder().build())
      .build()]).build()).build()
    1 * loadBalancerClient.updateBackendSet(_) >> UpdateBackendSetResponse.builder().opcWorkRequestId("wr1").build()
    1 * OracleWorkRequestPoller.poll(_, _, _, loadBalancerClient) >> null
    1 * sgProvider.getServerGroup(_, _, _) >> sgViewMock
    sgViewMock.instanceCounts >> instanceCounts
  }
}
