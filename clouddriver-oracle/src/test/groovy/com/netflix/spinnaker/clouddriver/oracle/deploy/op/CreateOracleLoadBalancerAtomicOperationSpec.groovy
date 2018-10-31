/*
 * Copyright (c) 2017, 2018, Oracle Corporation and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */
package com.netflix.spinnaker.clouddriver.oracle.deploy.op

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.oracle.deploy.OracleWorkRequestPoller
import com.netflix.spinnaker.clouddriver.oracle.deploy.converter.CreateOracleLoadBalancerAtomicOperationConverter
import com.netflix.spinnaker.clouddriver.oracle.deploy.description.CreateLoadBalancerDescription
import com.netflix.spinnaker.clouddriver.oracle.security.OracleNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import com.oracle.bmc.loadbalancer.LoadBalancerClient
import com.oracle.bmc.loadbalancer.model.CreateLoadBalancerDetails
import com.oracle.bmc.loadbalancer.requests.CreateLoadBalancerRequest
import com.oracle.bmc.loadbalancer.responses.CreateLoadBalancerResponse
import spock.lang.Shared
import spock.lang.Specification

class CreateOracleLoadBalancerAtomicOperationSpec extends Specification {
  
  @Shared
  ObjectMapper mapper = new ObjectMapper()

  @Shared
  CreateOracleLoadBalancerAtomicOperationConverter converter

  def setupSpec() {
    this.converter = new CreateOracleLoadBalancerAtomicOperationConverter(objectMapper: mapper)
    converter.accountCredentialsProvider = Mock(AccountCredentialsProvider)
    converter.accountCredentialsProvider.getCredentials(_) >> Mock(OracleNamedAccountCredentials)
  }

  def "Create LoadBalancer"() {
    setup:
    def req = read('createLoadBalancer1.json')
    def desc = converter.convertDescription(req[0].upsertLoadBalancer)

    def creds = Mock(OracleNamedAccountCredentials)
    def loadBalancerClient = Mock(LoadBalancerClient)
    creds.loadBalancerClient >> loadBalancerClient
    desc.credentials = creds

    GroovySpy(OracleWorkRequestPoller, global: true)

    TaskRepository.threadLocalTask.set(Mock(Task))
    def op = new CreateOracleLoadBalancerAtomicOperation(desc)

    when:
    op.operate(null)

    then:

    1 * loadBalancerClient.createLoadBalancer(_) >> { args ->
      CreateLoadBalancerDetails lb = args[0].getCreateLoadBalancerDetails()
      def listener = lb.listeners.get('HTTP_80')
      assert lb.getIsPrivate()
      assert lb.getShapeName() == '400Mbps'
      assert lb.listeners.size() == 1
      assert listener.port == 80
      assert listener.protocol == 'HTTP'
      assert listener.defaultBackendSetName == 'backendSet1'
      assert lb.backendSets.size() == 1
      assert lb.backendSets.backendSet1.policy == 'ROUND_ROBIN'
      assert lb.backendSets.backendSet1.healthChecker.port == 80
      assert lb.backendSets.backendSet1.healthChecker.protocol == 'HTTP'
      assert lb.backendSets.backendSet1.healthChecker.urlPath == '/healthZ'
      CreateLoadBalancerResponse.builder().opcWorkRequestId("wr1").build()
    }
    1 * OracleWorkRequestPoller.poll("wr1", _, _, loadBalancerClient) >> null
  }

  def "Create LoadBalancer with 2 Listeners"() {
    setup:
    def req = read('createLoadBalancer2.json')
    def desc = converter.convertDescription(req[0].upsertLoadBalancer)

    def creds = Mock(OracleNamedAccountCredentials)
    def loadBalancerClient = Mock(LoadBalancerClient)
    creds.loadBalancerClient >> loadBalancerClient
    desc.credentials = creds

    GroovySpy(OracleWorkRequestPoller, global: true)

    TaskRepository.threadLocalTask.set(Mock(Task))
    def op = new CreateOracleLoadBalancerAtomicOperation(desc)

    when:
    op.operate(null)

    then:

    1 * loadBalancerClient.createLoadBalancer(_) >> { args ->
      CreateLoadBalancerDetails lb = args[0].getCreateLoadBalancerDetails()
      assert lb.getIsPrivate()
      assert lb.listeners.size() == 2
      assert lb.listeners.httpListener.port == 8080
      assert lb.listeners.httpListener.protocol == 'HTTP'
      assert lb.listeners.httpsListener.port == 8081
      assert lb.listeners.httpsListener.protocol == 'HTTPS'
      assert lb.backendSets.size() == 1
      assert lb.backendSets.myBackendSet.policy == 'ROUND_ROBIN'
      assert lb.backendSets.myBackendSet.healthChecker.port == 80
      CreateLoadBalancerResponse.builder().opcWorkRequestId("wr1").build()
    }
    1 * OracleWorkRequestPoller.poll("wr1", _, _, loadBalancerClient) >> null
  }

  def read(String fileName) {
    def json = new File(getClass().getResource('/desc/' + fileName).toURI()).text
    List<Map<String, Object>> data = mapper.readValue(json, new TypeReference<List<Map<String, Object>>>(){});
    return data;
  }

}
