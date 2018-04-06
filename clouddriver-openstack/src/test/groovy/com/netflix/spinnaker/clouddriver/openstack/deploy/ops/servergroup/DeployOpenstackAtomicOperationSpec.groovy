/*
 * Copyright 2016 The original authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.openstack.deploy.ops.servergroup

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.openstack.client.OpenstackClientProvider
import com.netflix.spinnaker.clouddriver.openstack.client.OpenstackProviderFactory
import com.netflix.spinnaker.clouddriver.openstack.config.OpenstackConfigurationProperties
import com.netflix.spinnaker.clouddriver.openstack.deploy.description.servergroup.DeployOpenstackAtomicOperationDescription
import com.netflix.spinnaker.clouddriver.openstack.deploy.description.servergroup.ServerGroupParameters
import com.netflix.spinnaker.clouddriver.openstack.deploy.description.servergroup.UserDataType
import com.netflix.spinnaker.clouddriver.openstack.deploy.exception.OpenstackOperationException
import com.netflix.spinnaker.clouddriver.openstack.deploy.exception.OpenstackProviderException
import com.netflix.spinnaker.clouddriver.openstack.security.OpenstackCredentials
import com.netflix.spinnaker.clouddriver.openstack.security.OpenstackNamedAccountCredentials
import org.openstack4j.model.heat.Stack
import org.openstack4j.model.network.Subnet
import org.openstack4j.model.network.ext.ListenerV2
import org.openstack4j.model.network.ext.LoadBalancerV2
import org.openstack4j.openstack.heat.domain.HeatStack
import org.openstack4j.openstack.networking.domain.ext.ListItem
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class DeployOpenstackAtomicOperationSpec extends Specification {
  String accountName = 'myaccount'
  String application = "app"
  String stack = "stack"
  String details = "details"
  String region = "region"
  Integer timeoutMins = 5
  Boolean disableRollback = false
  String instanceType = 'm1.small'
  int externalPort = 80
  int internalPort = 8100
  String image = 'ubuntu-latest'
  int maxSize = 5
  int minSize = 3
  String subnetId = '1234'
  String lbId = '5678'
  String listenerId = '9999'
  String poolId = '8888'
  List<String> securityGroups = ['sg1']

  def credentials
  def serverGroupParams
  def expectedServerGroupParams
  def description
  def provider
  def mockLb
  def mockListener
  def mockItem
  def mockSubnet
  def tags

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  def setup() {
    provider = Mock(OpenstackClientProvider)
    GroovyMock(OpenstackProviderFactory, global : true)
    OpenstackNamedAccountCredentials creds = Mock(OpenstackNamedAccountCredentials)
    creds.getStackConfig() >> new OpenstackConfigurationProperties.StackConfig(pollInterval: 0, pollTimeout: 1)
    OpenstackProviderFactory.createProvider(creds) >> { provider }
    credentials = new OpenstackCredentials(creds)

    serverGroupParams = new ServerGroupParameters(instanceType: instanceType, image:image, maxSize: maxSize, minSize: minSize, subnetId: subnetId, loadBalancers: [lbId], securityGroups: securityGroups)
    description = new DeployOpenstackAtomicOperationDescription(stack: stack, application: application, freeFormDetails: details, region: region, serverGroupParameters: serverGroupParams.clone(), timeoutMins: timeoutMins, disableRollback: disableRollback, account: accountName, credentials: credentials)

    // Add the computed parts to the server group params
    expectedServerGroupParams = serverGroupParams.clone()
    expectedServerGroupParams.with {
      it.networkId = '1234'
      it.rawUserData = ''
    }

    mockItem = Mock(ListItem)
    mockItem.id >> { listenerId }
    mockLb = Mock(LoadBalancerV2)
    mockLb.name >> { 'mockpool' }
    mockLb.listeners >> {[mockItem]}
    mockListener = Mock(ListenerV2)
    mockListener.id >> { listenerId }
    mockListener.defaultPoolId >> { poolId }
    mockListener.description >> { "HTTP:$externalPort:HTTP:$internalPort" }
    mockSubnet = Mock(Subnet)
    mockSubnet.networkId >> { '1234' }
    tags = [lbId]
  }

  def "should deploy a heat stack"() {
    given:
    @Subject def operation = new DeployOpenstackAtomicOperation(description)
    String createdStackName = 'app-stack-details-v000'

    when:
    operation.operate([])

    then:
    1 * provider.listStacks(region) >> []
    1 * provider.getLoadBalancer(region, lbId) >> mockLb
    1 * provider.getListener(region, listenerId) >> mockListener
    1 * provider.getSubnet(region, subnetId) >> mockSubnet
    1 * provider.deploy(region, createdStackName, _ as String, _ as Map<String,String>, expectedServerGroupParams, _ as Boolean, _ as Long, tags)
    1 * provider.getStack(region, createdStackName) >> new HeatStack(status: "CREATE_COMPLETE")
    noExceptionThrown()
  }

  def "should deploy a heat stack even when stack exists"() {
    given:
    @Subject def operation = new DeployOpenstackAtomicOperation(description)
    Stack stack = Mock(Stack)
    String createdStackName = 'app-stack-details-v000'
    stack.name >> { createdStackName }
    stack.creationTime >> { '2014-06-03T20:59:46Z' }
    String newStackName = 'app-stack-details-v001'

    when:
    operation.operate([])

    then:
    1 * provider.listStacks(_) >> [stack]
    1 * provider.getLoadBalancer(region, lbId) >> mockLb
    1 * provider.getListener(region, listenerId) >> mockListener
    1 * provider.getSubnet(region, subnetId) >> mockSubnet
    1 * provider.deploy(region, newStackName, _ as String, _ as Map<String,String>, expectedServerGroupParams, _ as Boolean, _ as Long, tags)
    1 * provider.getStack(region, newStackName) >> new HeatStack(status: "CREATE_COMPLETE")
    noExceptionThrown()
  }

  def "should deploy a heat stack with scaleup and scaledown"() {
    given:
    def scaledServerGroupParams = serverGroupParams.clone()
    scaledServerGroupParams.with {
      it.autoscalingType = ServerGroupParameters.AutoscalingType.CPU
      it.scaleup = new ServerGroupParameters.Scaler(cooldown: 60, adjustment: 1, period: 60, threshold: 50)
      it.scaledown = new ServerGroupParameters.Scaler(cooldown: 60, adjustment: -1, period: 600, threshold: 15)
    }

    def scaledDescription = description.clone()
    scaledDescription.with {
      it.serverGroupParameters = scaledServerGroupParams.clone()
    }

    def expected = expectedServerGroupParams.clone()
    expected.with {
      it.autoscalingType = ServerGroupParameters.AutoscalingType.CPU
      it.scaleup = new ServerGroupParameters.Scaler(cooldown: 60, adjustment: 1, period: 60, threshold: 50)
      it.scaledown = new ServerGroupParameters.Scaler(cooldown: 60, adjustment: -1, period: 600, threshold: 15)
    }

    @Subject def operation = new DeployOpenstackAtomicOperation(scaledDescription)
    String createdStackName = 'app-stack-details-v000'

    when:
    operation.operate([])

    then:
    1 * provider.listStacks(region) >> []
    1 * provider.getLoadBalancer(region, lbId) >> mockLb
    1 * provider.getListener(region, listenerId) >> mockListener
    1 * provider.getSubnet(region, subnetId) >> mockSubnet
    1 * provider.deploy(region, createdStackName, _ as String, _ as Map<String,String>, expected, _ as Boolean, _ as Long, tags)
    1 * provider.getStack(region, createdStackName) >> new HeatStack(status: "CREATE_COMPLETE")
    noExceptionThrown()
  }

  def "ensure user data is resolved correctly"() {
    def userData = '#!/bin/bash\necho "userdata" >> /etc/userdata'
    def expected = expectedServerGroupParams.clone()
    expected.with {
      it.rawUserData = userData
      it.sourceUserDataType = UserDataType.TEXT.toString()
      it.sourceUserData = userData
    }

    def userDataDescription = description.clone()
    userDataDescription.with {
      it.userData = userData
      it.userDataType = UserDataType.TEXT
    }
    @Subject def operation = new DeployOpenstackAtomicOperation(userDataDescription)
    String createdStackName = 'app-stack-details-v000'

    when:
    operation.operate([])

    then:
    1 * provider.listStacks(region) >> []
    1 * provider.getLoadBalancer(region, lbId) >> mockLb
    1 * provider.getListener(region, listenerId) >> mockListener
    1 * provider.getSubnet(region, subnetId) >> mockSubnet
    1 * provider.deploy(region, createdStackName, _ as String, _ as Map<String,String>, expected, _ as Boolean, _ as Long, tags)
    1 * provider.getStack(region, createdStackName) >> new HeatStack(status: "CREATE_COMPLETE")
    noExceptionThrown()
  }

  def "should not deploy a stack when exception thrown"() {
    given:
    @Subject def operation = new DeployOpenstackAtomicOperation(description)
    String createdStackName = 'app-stack-details-v000'
    Throwable throwable = new OpenstackProviderException('foo')

    when:
    operation.operate([])

    then:
    1 * provider.listStacks(region) >> []
    1 * provider.getLoadBalancer(region, lbId) >> mockLb
    1 * provider.getListener(region, listenerId) >> mockListener
    1 * provider.getSubnet(region, subnetId) >> mockSubnet
    1 * provider.deploy(region, createdStackName, _ as String, _ as Map<String,String>, expectedServerGroupParams, _ as Boolean, _ as Long, tags) >> { throw throwable }
    0 * provider.getStack(region, createdStackName)
    Throwable actual = thrown(OpenstackOperationException)
    actual.cause == throwable
  }

  def "should throw an exception when stack creation fails"() {
    given:
    @Subject def operation = new DeployOpenstackAtomicOperation(description)
    String createdStackName = 'app-stack-details-v000'

    when:
    operation.operate([])

    then:
    1 * provider.listStacks(region) >> []
    1 * provider.getLoadBalancer(region, lbId) >> mockLb
    1 * provider.getListener(region, listenerId) >> mockListener
    1 * provider.getSubnet(region, subnetId) >> mockSubnet
    1 * provider.deploy(region, createdStackName, _ as String, _ as Map<String,String>, expectedServerGroupParams, _ as Boolean, _ as Long, tags)
    1 * provider.getStack(region, createdStackName) >> new HeatStack(status: "CREATE_FAILED")
    thrown(OpenstackOperationException)
  }

  def "should retry when the stack is pending"() {
    given:
    @Subject def operation = new DeployOpenstackAtomicOperation(description)
    String createdStackName = 'app-stack-details-v000'

    when:
    operation.operate([])

    then:
    1 * provider.listStacks(region) >> []
    1 * provider.getLoadBalancer(region, lbId) >> mockLb
    1 * provider.getListener(region, listenerId) >> mockListener
    1 * provider.getSubnet(region, subnetId) >> mockSubnet
    1 * provider.deploy(region, createdStackName, _ as String, _ as Map<String,String>, expectedServerGroupParams, _ as Boolean, _ as Long, tags)
    2 * provider.getStack(region, createdStackName) >>> [new HeatStack(status: "CREATE_IN_PROGRESS"), new HeatStack(status: "CREATE_COMPLETE")]
    noExceptionThrown()
  }

  @Unroll
  def "creates HEAT template: #type"() {
    given:
    def mapper = new ObjectMapper(new YAMLFactory())
    @Subject def operation = new DeployOpenstackAtomicOperation(description)
    String createdStackName = 'app-stack-details-v000'
    if (fip) {
      description.serverGroupParameters.floatingNetworkId = "net-9876"
    }

    if (!loadBalancers) {
      description.serverGroupParameters.loadBalancers = []
      tags = []
    }

    when:
    operation.operate([])

    then:
    1 * provider.listStacks(region) >> []
    if (loadBalancers) {
      1 * provider.getLoadBalancer(region, lbId) >> mockLb
      1 * provider.getListener(region, listenerId) >> mockListener
    }
    1 * provider.getSubnet(region, subnetId) >> mockSubnet
    1 * provider.deploy(region, createdStackName, { assertTemplate(it, mainTemplate) }, { assertTemplates(it, subtemplates)}, { params(it) }, _ as Boolean, _ as Long, tags)
    1 * provider.getStack(region, createdStackName) >> new HeatStack(status: "CREATE_COMPLETE")
    noExceptionThrown()

    where:
    type                        | fip   | loadBalancers || mainTemplate                              | subtemplates                                                                                                                                | params
    "no fip, no load balancers" | false | false         || exampleTemplate("servergroup.yaml")       | ["servergroup_resource.yaml": exampleTemplate("servergroup_server.yaml")]                                                                   | { ServerGroupParameters params -> true }
    "fip, no load balancers"    | true  | false         || exampleTemplate("servergroup_float.yaml") | ["servergroup_resource.yaml": exampleTemplate("servergroup_server_float.yaml")]                                                             | { ServerGroupParameters params -> true }
    "no fip, load balancers"    | false | true          || exampleTemplate("servergroup.yaml")       | ["servergroup_resource.yaml": exampleTemplate("servergroup_resource.yaml"), "servergroup_resource_member.yaml": memberDataTemplate()]       | { ServerGroupParameters params -> true }
    "fip, load balancers"       | true  | true          || exampleTemplate("servergroup_float.yaml") | ["servergroup_resource.yaml": exampleTemplate("servergroup_resource_float.yaml"), "servergroup_resource_member.yaml": memberDataTemplate()] | { ServerGroupParameters params -> true }
  }

  private boolean assertTemplate(String actual, String expected) {
    def mapper = new ObjectMapper(new YAMLFactory())
    return mapper.readValue(actual, Map) == mapper.readValue(expected, Map)
  }

  private boolean assertTemplates(Map actual, Map expected) {
    def mapper = new ObjectMapper(new YAMLFactory())
    return actual.collectEntries {k, v -> [(k): mapper.readValue(v, Map)]} == expected.collectEntries { k, v -> [(k): mapper.readValue(v, Map)] }
  }

  private String exampleTemplate(String name) {
    DeployOpenstackAtomicOperationSpec.class.getResource(name).getText("utf-8")
  }

  private String memberDataTemplate() {
    return """\
---
heat_template_version: "2016-04-08"
description: "Pool members for autoscaling group resource"
parameters:
  address:
    type: "string"
    description: "Server address for autoscaling group resource"
resources:
  member-mockpool-99-null-null:
    type: "OS::Neutron::LBaaS::PoolMember"
    properties:
      address:
        get_param: "address"
      pool: "8888"
      protocol_port: null
      subnet: "1234"
"""
  }

}
