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
import com.netflix.spinnaker.clouddriver.oraclebmcs.deploy.OracleBMCSWorkRequestPoller
import com.netflix.spinnaker.clouddriver.oraclebmcs.deploy.description.CreateLoadBalancerDescription
import com.netflix.spinnaker.clouddriver.oraclebmcs.security.OracleBMCSNamedAccountCredentials
import com.oracle.bmc.loadbalancer.LoadBalancerClient
import com.oracle.bmc.loadbalancer.responses.CreateLoadBalancerResponse
import spock.lang.Specification

class CreateOracleBMCSLoadBalancerAtomicOperationSpec extends Specification {

  def "Create load balancer"() {
    setup:
    def desc = new CreateLoadBalancerDescription()
    desc.application = "foo"
    desc.stack = "dev"
    def creds = Mock(OracleBMCSNamedAccountCredentials)
    def loadBalancerClient = Mock(LoadBalancerClient)
    creds.loadBalancerClient >> loadBalancerClient
    desc.credentials = creds
    desc.healthCheck = new CreateLoadBalancerDescription.HealthCheck()
    desc.listener = new CreateLoadBalancerDescription.Listener()
    GroovySpy(OracleBMCSWorkRequestPoller, global: true)

    TaskRepository.threadLocalTask.set(Mock(Task))
    def op = new CreateOracleBMCSLoadBalancerAtomicOperation(desc)

    when:
    op.operate(null)

    then:

    1 * loadBalancerClient.createLoadBalancer(_) >> CreateLoadBalancerResponse.builder().opcWorkRequestId("wr1").build()
    1 * OracleBMCSWorkRequestPoller.poll("wr1", _, _, loadBalancerClient) >> null
  }

}
