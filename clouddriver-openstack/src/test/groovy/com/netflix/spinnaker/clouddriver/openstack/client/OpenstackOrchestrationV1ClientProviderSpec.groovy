/*
 * Copyright 2016 Target, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.openstack.client

import com.netflix.spinnaker.clouddriver.openstack.deploy.description.servergroup.ServerGroupParameters
import com.netflix.spinnaker.clouddriver.openstack.deploy.exception.OpenstackProviderException
import org.openstack4j.api.Builders
import org.openstack4j.api.exceptions.ServerResponseException
import org.openstack4j.api.heat.HeatService
import org.openstack4j.api.heat.ResourcesService
import org.openstack4j.api.heat.StackService
import org.openstack4j.api.heat.TemplateService
import org.openstack4j.model.common.ActionResponse
import org.openstack4j.model.heat.Resource
import org.openstack4j.model.heat.Stack
import org.openstack4j.model.heat.StackCreate
import org.openstack4j.model.heat.StackUpdate
import org.springframework.http.HttpStatus

class OpenstackOrchestrationV1ClientProviderSpec extends OpenstackClientProviderSpec {

  def "deploy heat stack succeeds"() {
    setup:
    Stack stack = Mock(Stack)
    HeatService heat = Mock(HeatService)
    StackService stackApi = Mock(StackService)
    mockClient.heat() >> heat
    heat.stacks() >> stackApi
    String stackName = "mystack"
    String tmpl = "foo: bar"
    Map<String, String> subtmpl = [sub: "foo: bar"]
    String region = 'region'
    boolean disableRollback = false
    int timeoutMins = 5
    String instanceType = 'm1.small'
    String image = 'ubuntu-latest'
    int maxSize = 5
    int minSize = 3
    int desiredSize = 4
    String subnetId = '9999'
    String networkId = '1234'
    List<String> loadBalancerIds = ['5678']
    List<String> securityGroups = ['sg1']
    String resourceFileName = 'servergroup_resource'
    ServerGroupParameters parameters = new ServerGroupParameters(instanceType: instanceType, image: image,
      maxSize: maxSize, minSize: minSize, desiredSize: desiredSize,
      subnetId: subnetId, networkId: networkId, loadBalancers: loadBalancerIds, securityGroups: securityGroups,
      autoscalingType: ServerGroupParameters.AutoscalingType.CPU,
      scaleup: new ServerGroupParameters.Scaler(cooldown: 60, period: 60, adjustment: 1, threshold: 50),
      scaledown: new ServerGroupParameters.Scaler(cooldown: 60, period: 600, adjustment: -1, threshold: 15),
      rawUserData: 'echo foobar',
      tags: ['foo': 'bar'],
      sourceUserDataType: 'Text',
      sourceUserData: 'echo foobar',
      resourceFilename: resourceFileName,
      zones: ["az1","az2"]
    )
    Map<String, String> params = [
      flavor               : parameters.instanceType,
      image                : parameters.image,
      max_size             : "$parameters.maxSize".toString(),
      min_size             : "$parameters.minSize".toString(),
      desired_size         : "$parameters.desiredSize".toString(),
      network_id           : parameters.networkId,
      subnet_id            : "$parameters.subnetId".toString(),
      load_balancers       : parameters.loadBalancers.join(','),
      security_groups      : parameters.securityGroups.join(','),
      autoscaling_type     : 'cpu_util',
      scaleup_cooldown     : 60,
      scaleup_adjustment   : 1,
      scaleup_period       : 60,
      scaleup_threshold    : 50,
      scaledown_cooldown   : 60,
      scaledown_adjustment : -1,
      scaledown_period     : 600,
      scaledown_threshold  : 15,
      source_user_data_type: 'Text',
      source_user_data     : 'echo foobar',
      tags                 : '{"foo":"bar"}',
      user_data            : parameters.rawUserData,
      resource_filename    : resourceFileName,
      zones                : 'az1,az2'
    ]
    List<String> tags = loadBalancerIds.collect { "lb-${it}" }
    StackCreate stackCreate = Builders.stack().disableRollback(disableRollback).files(subtmpl).name(stackName).parameters(params).template(tmpl).timeoutMins(timeoutMins).tags(tags.join(',')).build()

    when:
    provider.deploy(region, stackName, tmpl, subtmpl, parameters, disableRollback, timeoutMins, tags)

    then:
    1 * stackApi.create(_ as StackCreate) >> { StackCreate sc ->
      assert sc.disableRollback == stackCreate.disableRollback
      assert sc.name == stackCreate.name
      assert sc.parameters.toString() == stackCreate.parameters.toString()
      assert sc.template == stackCreate.template
      stack
    }
    noExceptionThrown()
  }

  def "deploy heat stack fails - exception"() {
    setup:
    HeatService heat = Mock(HeatService)
    StackService stackApi = Mock(StackService)
    mockClient.heat() >> heat
    heat.stacks() >> stackApi
    String stackName = "mystack"
    String tmpl = "foo: bar"
    Map<String, String> subtmpl = [sub: "foo: bar"]
    String region = 'region'
    boolean disableRollback = false
    int timeoutMins = 5
    String instanceType = 'm1.small'
    String image = 'ubuntu-latest'
    int maxSize = 5
    int minSize = 3
    String networkId = '1234'
    List<String> loadBalancerIds = ['5678']
    List<String> securityGroups = ['sg1']
    List<String> tags = loadBalancerIds.collect { "lb-${it}" }
    ServerGroupParameters parameters = new ServerGroupParameters(instanceType: instanceType, image: image, maxSize: maxSize, minSize: minSize, networkId: networkId, loadBalancers: loadBalancerIds, securityGroups: securityGroups)

    when:
    provider.deploy(region, stackName, tmpl, subtmpl, parameters, disableRollback, timeoutMins, tags)

    then:
    1 * stackApi.create(_ as StackCreate) >> { throw new Exception('foobar') }
    Exception e = thrown(OpenstackProviderException)
    e.message == 'Unable to process request'
    e.cause.message == 'foobar'
  }

  def "get instance ids for stack succeeds"() {
    setup:
    HeatService heat = Mock()
    ResourcesService resourcesService = Mock()
    String id1 = UUID.randomUUID().toString()
    String id2 = UUID.randomUUID().toString()
    Resource r1 = Stub() {
      getPhysicalResourceId() >> id1
      getType() >> "OS::Nova::Server"
    }
    Resource r2 = Stub() {
      getPhysicalResourceId() >> id2
      getType() >> "not:a:server"
    }
    List<? extends Resource> resources = [r1, r2]

    when:
    List<String> result = provider.getInstanceIdsForStack(region, "mystack")

    then:
    1 * mockClient.heat() >> heat
    1 * heat.resources() >> resourcesService
    1 * resourcesService.list("mystack", 10) >> resources
    result == [id1]
    noExceptionThrown()
  }

  def "get instance ids for stack throws exception"() {
    setup:
    HeatService heat = Mock()
    ResourcesService resourcesService = Mock()
    Throwable throwable = new ServerResponseException('foo', HttpStatus.INTERNAL_SERVER_ERROR.value())

    when:
    provider.getInstanceIdsForStack(region, "mystack")

    then:
    1 * mockClient.heat() >> heat
    1 * heat.resources() >> resourcesService
    1 * resourcesService.list("mystack", 10) >> { throw throwable }

    and:
    OpenstackProviderException openstackProviderException = thrown(OpenstackProviderException)
    openstackProviderException.cause == throwable
  }

  def "get heat template succeeds"() {
    setup:
    HeatService heat = Mock()
    TemplateService templateApi = Mock()
    mockClient.useRegion(_ as String).heat() >> heat
    heat.templates() >> templateApi

    when:
    provider.getHeatTemplate("myregion", "mystack", "mystackid")

    then:
    1 * mockClient.useRegion("myregion") >> mockClient
    1 * mockClient.heat() >> heat
    1 * templateApi.getTemplateAsString("mystack", "mystackid")
    noExceptionThrown()
  }

  def "get stack succeeds"() {
    setup:
    String stackName = 'stackofpancakesyumyum'
    HeatService heatService = Mock(HeatService)
    StackService stackService = Mock(StackService)
    mockClient.heat() >> heatService
    heatService.stacks() >> stackService
    Stack mockStack = Mock(Stack)

    when:
    Stack actual = provider.getStack('region1', stackName)

    then:
    1 * stackService.getStackByName(stackName) >> mockStack
    actual == mockStack
  }

  def "get stack does not find stack"() {
    given:
    HeatService heat = Mock()
    StackService stackApi = Mock()
    mockClient.heat() >> heat
    heat.stacks() >> stackApi
    def name = 'mystack'

    when:
    provider.getStack(region, name)

    then:
    1 * stackApi.getStackByName(name) >> null
    def ex = thrown(OpenstackProviderException)
    ex.message.contains(name)
  }

  def "get stack fails - exception"() {
    setup:
    String stackName = 'stackofpancakesyumyum'
    HeatService heatService = Mock(HeatService)
    StackService stackService = Mock(StackService)
    mockClient.heat() >> heatService
    heatService.stacks() >> stackService

    when:
    provider.getStack('region1', stackName)

    then:
    1 * stackService.getStackByName(stackName) >> { throw new Exception('foo') }
    Exception e = thrown(OpenstackProviderException)
    e.cause.message == 'foo'
  }

  def "delete stack succeeds"() {
    setup:
    def success = ActionResponse.actionSuccess()
    String stackName = 'stackofpancakesyumyum'
    String stackId = UUID.randomUUID().toString()
    HeatService heatService = Mock(HeatService)
    StackService stackService = Mock(StackService)
    mockClient.heat() >> heatService
    heatService.stacks() >> stackService
    Stack mockStack = Mock(Stack)
    mockStack.name >> stackName
    mockStack.id >> stackId

    when:
    provider.destroy('region1', mockStack)

    then:
    1 * stackService.delete(stackName, stackId) >> success
  }

  def "delete stack fails - exception"() {
    setup:
    String stackName = 'stackofpancakesyumyum'
    String stackId = UUID.randomUUID().toString()
    HeatService heatService = Mock(HeatService)
    StackService stackService = Mock(StackService)
    mockClient.heat() >> heatService
    heatService.stacks() >> stackService
    Stack mockStack = Mock(Stack)
    mockStack.name >> stackName
    mockStack.id >> stackId

    when:
    provider.destroy('region1', mockStack)

    then:
    1 * stackService.delete(stackName, stackId) >> { throw new Exception('foo') }
    Exception e = thrown(OpenstackProviderException)
    e.cause.message == 'foo'
  }

  def "delete stack fails - failed status"() {
    setup:
    ActionResponse failed = ActionResponse.actionFailed('foo', 400)
    String stackName = 'stackofpancakesyumyum'
    String stackId = UUID.randomUUID().toString()
    HeatService heatService = Mock(HeatService)
    StackService stackService = Mock(StackService)
    mockClient.heat() >> heatService
    heatService.stacks() >> stackService
    Stack mockStack = Mock(Stack)
    mockStack.name >> stackName
    mockStack.id >> stackId

    when:
    provider.destroy('region1', mockStack)

    then:
    1 * stackService.delete(stackName, stackId) >> failed
    Exception e = thrown(OpenstackProviderException)
    e.message == "Action request failed with fault foo and code 400"
  }

  def "resize stack succeeds"() {
    setup:
    ActionResponse success = ActionResponse.actionSuccess()
    String stackName = 'stackofpancakesyumyum'
    String stackId = UUID.randomUUID().toString()
    HeatService heatService = Mock(HeatService)
    StackService stackService = Mock(StackService)
    mockClient.heat() >> heatService
    heatService.stacks() >> stackService
    Stack mockStack = Mock(Stack)
    mockStack.name >> stackName
    mockStack.id >> stackId
    String region = 'r1'
    String instanceType = 'm1.small'
    String image = 'ubuntu-latest'
    int maxSize = 5
    int minSize = 3
    int desiredSize = 4
    String networkId = '1234'
    String subnetId = '9999'
    List<String> loadBalancerIds = ['5678']
    List<String> securityGroups = ['sg1']
    String resourceFileName = 'servergroup_resource'
    ServerGroupParameters parameters = new ServerGroupParameters(instanceType: instanceType, image: image,
      maxSize: maxSize, minSize: minSize, desiredSize: desiredSize, networkId: networkId, subnetId: subnetId,
      loadBalancers: loadBalancerIds, securityGroups: securityGroups, rawUserData: 'echo foobar', tags: ['foo': 'bar'],
      sourceUserDataType: 'Text', sourceUserData: 'echo foobar', resourceFilename: resourceFileName, zones: ["az1","az2"])
    Map<String, String> params = [
      flavor               : parameters.instanceType,
      image                : parameters.image,
      max_size             : "$parameters.maxSize".toString(),
      min_size             : "$parameters.minSize".toString(),
      desired_size         : "$parameters.desiredSize".toString(),
      network_id           : parameters.networkId,
      subnet_id            : parameters.subnetId,
      load_balancers       : parameters.loadBalancers.join(','),
      security_groups      : parameters.securityGroups.join(','),
      autoscaling_type     : null,
      scaleup_cooldown     : null,
      scaleup_adjustment   : null,
      scaleup_period       : null,
      scaleup_threshold    : null,
      scaledown_cooldown   : null,
      scaledown_adjustment : null,
      scaledown_period     : null,
      scaledown_threshold  : null,
      source_user_data_type: 'Text',
      source_user_data     : 'echo foobar',
      tags                 : '{"foo":"bar"}',
      user_data            : parameters.rawUserData,
      resource_filename    : resourceFileName,
      zones                : 'az1,az2'
    ]
    String template = "foo: bar"
    Map<String, String> subtmpl = [sub: "foo: bar"]
    List<String> tags = loadBalancerIds.collect { "lb-${it}" }

    when:
    provider.updateStack(region, stackName, stackId, template, subtmpl, parameters, tags)

    then:
    1 * stackService.update(stackName, stackId, _ as StackUpdate) >> { String name, String id, StackUpdate su ->
      assert name == stackName
      assert id == stackId
      assert su.parameters.toString() == params.toString()
      success
    }
    noExceptionThrown()
  }

  def "resize stack failed - failed status"() {
    setup:
    ActionResponse failed = ActionResponse.actionFailed('ERROR', 500)
    String stackName = 'stackofpancakesyumyum'
    String stackId = UUID.randomUUID().toString()
    HeatService heatService = Mock(HeatService)
    StackService stackService = Mock(StackService)
    mockClient.heat() >> heatService
    heatService.stacks() >> stackService
    Stack mockStack = Mock(Stack)
    mockStack.name >> stackName
    mockStack.id >> stackId
    String region = 'r1'
    String instanceType = 'm1.small'
    String image = 'ubuntu-latest'
    int maxSize = 5
    int minSize = 3
    String networkId = '1234'
    List<String> loadBalancerIds = ['5678']
    List<String> securityGroups = ['sg1']
    ServerGroupParameters parameters = new ServerGroupParameters(instanceType: instanceType, image: image, maxSize: maxSize, minSize: minSize, networkId: networkId, loadBalancers: loadBalancerIds, securityGroups: securityGroups)
    String template = "foo: bar"
    Map<String, String> subtmpl = [sub: "foo: bar"]
    List<String> tags = loadBalancerIds.collect { "lb-${it}" }

    when:
    provider.updateStack(region, stackName, stackId, template, subtmpl, parameters, tags)

    then:
    1 * stackService.update(stackName, stackId, _ as StackUpdate) >> { String name, String id, StackUpdate su ->
      failed
    }
    thrown(OpenstackProviderException)
  }

  def "resize stack failed - exception thrown"() {
    setup:
    String stackName = 'stackofpancakesyumyum'
    String stackId = UUID.randomUUID().toString()
    HeatService heatService = Mock(HeatService)
    StackService stackService = Mock(StackService)
    mockClient.heat() >> heatService
    heatService.stacks() >> stackService
    Stack mockStack = Mock(Stack)
    mockStack.name >> stackName
    mockStack.id >> stackId
    String region = 'r1'
    String instanceType = 'm1.small'
    String image = 'ubuntu-latest'
    int maxSize = 5
    int minSize = 3
    String networkId = '1234'
    List<String> loadBalancerIds = ['5678']
    List<String> securityGroups = ['sg1']
    ServerGroupParameters parameters = new ServerGroupParameters(instanceType: instanceType, image: image, maxSize: maxSize, minSize: minSize, networkId: networkId, loadBalancers: loadBalancerIds, securityGroups: securityGroups)
    String template = "foo: bar"
    Map<String, String> subtmpl = [sub: "foo: bar"]
    List<String> tags = loadBalancerIds.collect { "lb-${it}" }

    when:
    provider.updateStack(region, stackName, stackId, template, subtmpl, parameters, tags)

    then:
    1 * stackService.update(stackName, stackId, _ as StackUpdate) >> { throw new Exception('foo') }
    thrown(OpenstackProviderException)
  }

}
