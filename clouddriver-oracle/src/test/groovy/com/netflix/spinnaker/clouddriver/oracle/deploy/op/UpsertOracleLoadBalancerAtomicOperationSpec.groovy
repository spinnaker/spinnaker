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
import com.netflix.spinnaker.clouddriver.oracle.deploy.converter.UpsertOracleLoadBalancerAtomicOperationConverter
import com.netflix.spinnaker.clouddriver.oracle.deploy.description.UpsertLoadBalancerDescription
import com.netflix.spinnaker.clouddriver.oracle.security.OracleNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import com.oracle.bmc.loadbalancer.LoadBalancerClient
import com.oracle.bmc.loadbalancer.model.Certificate
import com.oracle.bmc.loadbalancer.model.CreateLoadBalancerDetails
import com.oracle.bmc.loadbalancer.model.BackendDetails
import com.oracle.bmc.loadbalancer.model.BackendSet
import com.oracle.bmc.loadbalancer.model.BackendSetDetails
import com.oracle.bmc.loadbalancer.model.CreateBackendSetDetails
import com.oracle.bmc.loadbalancer.model.HealthCheckerDetails
import com.oracle.bmc.loadbalancer.model.Listener
import com.oracle.bmc.loadbalancer.model.ListenerDetails
import com.oracle.bmc.loadbalancer.model.LoadBalancer
import com.oracle.bmc.loadbalancer.model.UpdateBackendSetDetails
import com.oracle.bmc.loadbalancer.model.UpdateListenerDetails
import com.oracle.bmc.loadbalancer.requests.CreateBackendSetRequest
import com.oracle.bmc.loadbalancer.requests.CreateCertificateRequest
import com.oracle.bmc.loadbalancer.requests.CreateListenerRequest
import com.oracle.bmc.loadbalancer.requests.CreateLoadBalancerRequest
import com.oracle.bmc.loadbalancer.requests.DeleteBackendSetRequest
import com.oracle.bmc.loadbalancer.requests.DeleteCertificateRequest
import com.oracle.bmc.loadbalancer.requests.DeleteListenerRequest
import com.oracle.bmc.loadbalancer.requests.UpdateBackendSetRequest
import com.oracle.bmc.loadbalancer.requests.UpdateListenerRequest
import com.oracle.bmc.loadbalancer.responses.CreateBackendSetResponse
import com.oracle.bmc.loadbalancer.responses.CreateCertificateResponse
import com.oracle.bmc.loadbalancer.responses.CreateListenerResponse
import com.oracle.bmc.loadbalancer.responses.CreateLoadBalancerResponse
import com.oracle.bmc.loadbalancer.responses.DeleteBackendSetResponse
import com.oracle.bmc.loadbalancer.responses.DeleteCertificateResponse
import com.oracle.bmc.loadbalancer.responses.DeleteListenerResponse
import com.oracle.bmc.loadbalancer.responses.GetLoadBalancerResponse
import com.oracle.bmc.loadbalancer.responses.UpdateBackendSetResponse
import com.oracle.bmc.loadbalancer.responses.UpdateListenerResponse
import spock.lang.Shared
import spock.lang.Specification

class UpsertOracleLoadBalancerAtomicOperationSpec extends Specification {
  
  @Shared
  ObjectMapper mapper = new ObjectMapper()

  @Shared
  UpsertOracleLoadBalancerAtomicOperationConverter converter

  def setupSpec() {
    this.converter = new UpsertOracleLoadBalancerAtomicOperationConverter(objectMapper: mapper)
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

    OracleWorkRequestPoller.poller = Mock(OracleWorkRequestPoller)

    TaskRepository.threadLocalTask.set(Mock(Task))
    def op = new UpsertOracleLoadBalancerAtomicOperation(desc)

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
    1 * OracleWorkRequestPoller.poller.wait("wr1", _, _, loadBalancerClient) >> null
  }

  def "Create LoadBalancer with 2 Listeners"() {
    setup:
    def req = read('createLoadBalancer2.json')
    def desc = converter.convertDescription(req[0].upsertLoadBalancer)

    def creds = Mock(OracleNamedAccountCredentials)
    def loadBalancerClient = Mock(LoadBalancerClient)
    creds.loadBalancerClient >> loadBalancerClient
    desc.credentials = creds

    OracleWorkRequestPoller.poller = Mock(OracleWorkRequestPoller)

    TaskRepository.threadLocalTask.set(Mock(Task))
    def op = new UpsertOracleLoadBalancerAtomicOperation(desc)

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
    1 * OracleWorkRequestPoller.poller.wait("wr1", _, _, loadBalancerClient) >> null
  }

  def "Update LoadBalancer with BackendSets"() {
    setup:
    def loadBalancerId = 'updateLoadBalancerBackendSets';
    def req = read('updateLoadBalancerBackendSets.json')
    def desc = converter.convertDescription(req[0].upsertLoadBalancer)

    def creds = Mock(OracleNamedAccountCredentials)
    def loadBalancerClient = Mock(LoadBalancerClient)
    creds.loadBalancerClient >> loadBalancerClient
    desc.credentials = creds

    OracleWorkRequestPoller.poller = Mock(OracleWorkRequestPoller)

    TaskRepository.threadLocalTask.set(Mock(Task))
    def op = new UpsertOracleLoadBalancerAtomicOperation(desc)
    def backendSets = [ 
      // to be removed
      'myBackendSet0': BackendSet.builder().name('myBackendSet0').backends([]).build(), 
      // to be updated
      'myBackendSet1': BackendSet.builder().name('myBackendSet1').backends([]).build(), 
    ]

    when:
    op.operate(null)

    then:
    1 * loadBalancerClient.getLoadBalancer(_) >> 
      GetLoadBalancerResponse.builder().loadBalancer(LoadBalancer.builder().id(loadBalancerId).backendSets(backendSets).build()).build()
    1 * loadBalancerClient.deleteBackendSet(_) >> { args ->
      DeleteBackendSetRequest delBksReq = args[0]
      assert delBksReq.getLoadBalancerId() == loadBalancerId
      assert delBksReq.getBackendSetName() == 'myBackendSet0'
      DeleteBackendSetResponse.builder().opcWorkRequestId("wr0").build()
    }
    1 * OracleWorkRequestPoller.poller.wait("wr0", _, _, loadBalancerClient) >> null
    1 * loadBalancerClient.updateBackendSet(_) >> { args ->
      UpdateBackendSetRequest upBksReq = args[0]
      assert upBksReq.getLoadBalancerId() == loadBalancerId
      assert upBksReq.getBackendSetName() == 'myBackendSet1'
      UpdateBackendSetResponse.builder().opcWorkRequestId("wr1").build()
    }
    1 * OracleWorkRequestPoller.poller.wait("wr1", _, _, loadBalancerClient) >> null
    1 * loadBalancerClient.createBackendSet(_) >> { args ->
      CreateBackendSetRequest crBksReq = args[0]
      assert crBksReq.getLoadBalancerId() == loadBalancerId
      assert crBksReq.getCreateBackendSetDetails().getName() == 'myBackendSet2'
      CreateBackendSetResponse.builder().opcWorkRequestId("wr2").build()
    }
    1 * OracleWorkRequestPoller.poller.wait("wr2", _, _, loadBalancerClient) >> null
  }

  def "Update LoadBalancer with Certificates"() {
    setup:
    def loadBalancerId = 'updateLoadBalancerCerts';
    def req = read('updateLoadBalancerCerts.json')
    def desc = converter.convertDescription(req[0].upsertLoadBalancer)

    def creds = Mock(OracleNamedAccountCredentials)
    def loadBalancerClient = Mock(LoadBalancerClient)
    creds.loadBalancerClient >> loadBalancerClient
    desc.credentials = creds

    OracleWorkRequestPoller.poller = Mock(OracleWorkRequestPoller)

    TaskRepository.threadLocalTask.set(Mock(Task))
    def op = new UpsertOracleLoadBalancerAtomicOperation(desc)
    def certs = [ 
      // to be removed
      'cert0': Certificate.builder().certificateName('cert0').publicCertificate("cert0_pub").build(), 
      // to keep
      'cert1': Certificate.builder().certificateName('cert1').publicCertificate("cert1_pub").build(), 
    ]

    when:
    op.operate(null)

    then:
    1 * loadBalancerClient.getLoadBalancer(_) >> 
      GetLoadBalancerResponse.builder().loadBalancer(LoadBalancer.builder().id(loadBalancerId).certificates(certs).build()).build()
    1 * loadBalancerClient.deleteCertificate(_) >> { args ->
      DeleteCertificateRequest delCert = args[0]
      assert delCert.getLoadBalancerId() == loadBalancerId
      assert delCert.certificateName == 'cert0'
      DeleteCertificateResponse.builder().opcWorkRequestId("wr0").build()
    }
    1 * OracleWorkRequestPoller.poller.wait("wr0", _, _, loadBalancerClient) >> null
    1 * loadBalancerClient.createCertificate(_) >> { args ->
      CreateCertificateRequest crCertReq = args[0]
      assert crCertReq.getLoadBalancerId() == loadBalancerId
      assert crCertReq.getCreateCertificateDetails().certificateName == 'cert2'
      CreateCertificateResponse.builder().opcWorkRequestId("wr2").build()
    }
    1 * OracleWorkRequestPoller.poller.wait("wr2", _, _, loadBalancerClient) >> null
  }

  def "Update LoadBalancer with Listeners"() {
    setup:
    def loadBalancerId = 'updateLoadBalancerListeners';
    def req = read('updateLoadBalancerListeners.json')
    def desc = converter.convertDescription(req[0].upsertLoadBalancer)

    def creds = Mock(OracleNamedAccountCredentials)
    def loadBalancerClient = Mock(LoadBalancerClient)
    creds.loadBalancerClient >> loadBalancerClient
    desc.credentials = creds

    OracleWorkRequestPoller.poller = Mock(OracleWorkRequestPoller)

    TaskRepository.threadLocalTask.set(Mock(Task))
    def op = new UpsertOracleLoadBalancerAtomicOperation(desc)
    def listeners = [ 
      // to be removed
      'httpListener0': Listener.builder().name('httpListener0').protocol('HTTP').port(80).build(), 
      // to be updated
      'httpListener1': Listener.builder().name('httpListener1').protocol('HTTP').port(81).build(), 
    ]

    when:
    op.operate(null)

    then:
    1 * loadBalancerClient.getLoadBalancer(_) >> 
      GetLoadBalancerResponse.builder().loadBalancer(LoadBalancer.builder().id(loadBalancerId)
        .listeners(listeners).build()).build()
    1 * loadBalancerClient.deleteListener(_) >> { args ->
      DeleteListenerRequest dlLis = args[0]
      assert dlLis.getLoadBalancerId() == loadBalancerId
      assert dlLis.listenerName == 'httpListener0'
      DeleteListenerResponse.builder().opcWorkRequestId("wr0").build()
    }
    1 * OracleWorkRequestPoller.poller.wait("wr0", _, _, loadBalancerClient) >> null
    1 * loadBalancerClient.updateListener(_) >> { args ->
      UpdateListenerRequest upLis = args[0]
      assert upLis.getLoadBalancerId() == loadBalancerId
      assert upLis.listenerName == 'httpListener1'
      assert upLis.updateListenerDetails.port  == 8081
      UpdateListenerResponse.builder().opcWorkRequestId("wr1").build()
    }
    1 * OracleWorkRequestPoller.poller.wait("wr1", _, _, loadBalancerClient) >> null
    1 * loadBalancerClient.createListener(_) >> { args ->
      CreateListenerRequest crLis = args[0]
      assert crLis.getLoadBalancerId() == loadBalancerId
      assert crLis.createListenerDetails.name == 'httpsListener'
      assert crLis.createListenerDetails.port == 8082
      CreateListenerResponse.builder().opcWorkRequestId("wr2").build()
    }
    1 * OracleWorkRequestPoller.poller.wait("wr2", _, _, loadBalancerClient) >> null
  }

  def read(String fileName) {
    def json = new File(getClass().getResource('/desc/' + fileName).toURI()).text
    List<Map<String, Object>> data = mapper.readValue(json, new TypeReference<List<Map<String, Object>>>(){});
    return data;
  }
}
