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
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.openstack.deploy.ops.loadbalancer

import com.netflix.spinnaker.clouddriver.consul.config.ConsulConfig
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.openstack.client.BlockingStatusChecker
import com.netflix.spinnaker.clouddriver.openstack.client.OpenstackClientProvider
import com.netflix.spinnaker.clouddriver.openstack.client.OpenstackProviderFactory
import com.netflix.spinnaker.clouddriver.openstack.config.OpenstackConfigurationProperties
import com.netflix.spinnaker.clouddriver.openstack.deploy.description.OpenstackAtomicOperationDescription
import com.netflix.spinnaker.clouddriver.openstack.deploy.description.servergroup.ServerGroupParameters
import com.netflix.spinnaker.clouddriver.openstack.deploy.exception.OpenstackProviderException
import com.netflix.spinnaker.clouddriver.openstack.deploy.exception.OpenstackResourceNotFoundException
import com.netflix.spinnaker.clouddriver.openstack.deploy.ops.servergroup.ServerGroupConstants
import com.netflix.spinnaker.clouddriver.openstack.security.OpenstackCredentials
import com.netflix.spinnaker.clouddriver.openstack.security.OpenstackNamedAccountCredentials
import org.openstack4j.model.common.ActionResponse
import org.openstack4j.model.heat.Stack
import org.openstack4j.model.network.ext.LbPoolV2
import org.openstack4j.model.network.ext.ListenerV2
import org.openstack4j.model.network.ext.LoadBalancerV2
import org.openstack4j.openstack.networking.domain.ext.ListItem
import spock.lang.Shared
import spock.lang.Specification

class AbstractOpenstackLoadBalancerAtomicOperationSpec extends Specification {
  OpenstackClientProvider provider
  OpenstackCredentials credentials
  OpenstackAtomicOperationDescription description
  @Shared
  String region = 'region'
  @Shared
  String account = 'test'
  @Shared
  Throwable openstackProviderException = new OpenstackProviderException('foo')
  @Shared
  BlockingStatusChecker blockingClientAdapter = BlockingStatusChecker.from(60, 5) { true }
  @Shared
  String opName = 'TEST_PHASE'

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  def setup() {
    provider = Mock(OpenstackClientProvider)
    GroovyMock(OpenstackProviderFactory, global: true)
    OpenstackNamedAccountCredentials credz = new OpenstackNamedAccountCredentials("name", "test", "main", "user", "pw", "project", "domain", "endpoint", [], false, "", new OpenstackConfigurationProperties.LbaasConfig(pollTimeout: 60, pollInterval: 5), new ConsulConfig(), null)
    OpenstackProviderFactory.createProvider(credz) >> { provider }
    credentials = new OpenstackCredentials(credz)

    description = new OpenstackAtomicOperationDescription(credentials: credentials, region: region, account: account)
  }

  def "remove listeners and pools"() {
    given:
    String loadBalancerId = UUID.randomUUID()
    ListenerV2 listener = Mock(ListenerV2) {
      getId() >> '123'
    }
    String poolId = UUID.randomUUID()
    String healthMonitorId = UUID.randomUUID()
    LbPoolV2 lbPool = Mock(LbPoolV2) {
      getId() >> poolId
    }

    and:
    def operation = Spy(ObjectUnderTest, constructorArgs: [description])

    when:
    operation.deleteLoadBalancerPeripherals(opName, region, loadBalancerId, [listener])

    then:
    1 * operation.createBlockingActiveStatusChecker(credentials, region, loadBalancerId) >> blockingClientAdapter
    _ * listener.defaultPoolId >> poolId
    1 * provider.getPool(region, poolId) >> lbPool
    _ * lbPool.healthMonitorId >> healthMonitorId
    1 * operation.removeHealthMonitor(opName, region, loadBalancerId, healthMonitorId) >> {}
    1 * provider.deletePool(region, poolId) >> ActionResponse.actionSuccess()
    1 * provider.deleteListener(region, listener.id) >> ActionResponse.actionSuccess()
  }

  def "remove listeners and pools - no health monitor"() {
    given:
    String loadBalancerId = UUID.randomUUID()
    ListenerV2 listener = Mock(ListenerV2) {
      getId() >> '123'
    }
    String poolId = UUID.randomUUID()
    String healthMonitorId = null
    LbPoolV2 lbPool = Mock(LbPoolV2) {
      getId() >> poolId
    }

    and:
    def operation = Spy(ObjectUnderTest, constructorArgs: [description])

    when:
    operation.deleteLoadBalancerPeripherals(opName, region, loadBalancerId, [listener])

    then:
    1 * operation.createBlockingActiveStatusChecker(credentials, region, loadBalancerId) >> blockingClientAdapter
    _ * listener.defaultPoolId >> poolId
    1 * provider.getPool(region, poolId) >> lbPool
    _ * lbPool.healthMonitorId >> healthMonitorId
    0 * operation.removeHealthMonitor(opName, region, loadBalancerId, healthMonitorId) >> {}
    1 * provider.deletePool(region, poolId) >> ActionResponse.actionSuccess()
    1 * provider.deleteListener(region, listener.id) >> ActionResponse.actionSuccess()
  }

  def "remove listeners and pools - no pool"() {
    given:
    String loadBalancerId = UUID.randomUUID()
    ListenerV2 listener = Mock(ListenerV2) {
      getId() >> '123'
    }
    String poolId = null
    String healthMonitorId = UUID.randomUUID()
    LbPoolV2 lbPool = Mock(LbPoolV2) {
      getId() >> poolId
    }

    and:
    def operation = Spy(ObjectUnderTest, constructorArgs: [description])

    when:
    operation.deleteLoadBalancerPeripherals(opName, region, loadBalancerId, [listener])

    then:
    1 * operation.createBlockingActiveStatusChecker(credentials, region, loadBalancerId) >> blockingClientAdapter
    _ * listener.defaultPoolId >> poolId
    1 * provider.getPool(region, poolId) >> { throw new OpenstackResourceNotFoundException('test') }
    _ * lbPool.healthMonitorId >> healthMonitorId
    0 * operation.removeHealthMonitor(opName, region, loadBalancerId, healthMonitorId) >> {}
    0 * provider.deletePool(region, poolId) >> ActionResponse.actionSuccess()
    1 * provider.deleteListener(region, listener.id) >> ActionResponse.actionSuccess()
  }

  def "update server group success"() {
    given:
    String loadBalancerId = UUID.randomUUID()
    String stackId = UUID.randomUUID()
    String createdStackName = 'test-stack'
    String template = "foo: bar"
    Stack summary = Mock(Stack) {
      getName() >> createdStackName
    }
    Stack detail = Mock(Stack)
    Map<String, String> sub = ['servergroup_resource.yaml': 'foo: bar', 'servergroup_resource_member.yaml': 'foo: bar']
    List<String> tags = [loadBalancerId]
    ServerGroupParameters serverGroupParams = new ServerGroupParameters(loadBalancers: tags)
    LoadBalancerV2 loadBalancer = Mock(LoadBalancerV2)
    ListItem listItem = Mock(ListItem)
    String listenerId = UUID.randomUUID()
    ListenerV2 listener = Mock(ListenerV2) {
      getDescription() >> 'HTTP:80:HTTP:8080'
      getDefaultPoolId() >> UUID.randomUUID()
    }

    and:
    def operation = Spy(ObjectUnderTest, constructorArgs: [description])

    when:
    operation.updateServerGroup(opName, region, loadBalancerId)

    then:
    1 * provider.listStacksWithLoadBalancers(region, [loadBalancerId]) >> [summary]
    1 * provider.getStack(region, createdStackName) >> detail
    1 * provider.getHeatTemplate(region, createdStackName, stackId) >> template
    1 * detail.getOutputs() >> [[output_key: ServerGroupConstants.SUBTEMPLATE_OUTPUT, output_value: sub['servergroup_resource.yaml']], [output_key: ServerGroupConstants.MEMBERTEMPLATE_OUTPUT, output_value: sub['servergroup_resource_member.yaml']]]
    1 * detail.getParameters() >> serverGroupParams.toParamsMap()
    _ * detail.getId() >> stackId
    _ * detail.getName() >> createdStackName
    _ * detail.getTags() >> tags
    1 * provider.getLoadBalancer(region, loadBalancerId) >> loadBalancer
    1 * loadBalancer.listeners >> [listItem]
    _ * listItem.id >> listenerId
    1 * provider.getListener(region, listenerId) >> listener
    1 * provider.updateStack(region, createdStackName, stackId, template, _ as Map, _ as ServerGroupParameters, tags)
  }

  static class ObjectUnderTest extends AbstractOpenstackLoadBalancerAtomicOperation {
    OpenstackAtomicOperationDescription description

    ObjectUnderTest(OpenstackAtomicOperationDescription description) {
      super(description.credentials)
      this.description = description
    }
  }
}
