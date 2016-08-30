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

package com.netflix.spinnaker.clouddriver.openstack.controllers

import com.netflix.spinnaker.clouddriver.model.LoadBalancerInstance
import com.netflix.spinnaker.clouddriver.model.LoadBalancerServerGroup
import com.netflix.spinnaker.clouddriver.openstack.domain.HealthMonitor
import com.netflix.spinnaker.clouddriver.openstack.domain.HealthMonitor.HealthMonitorType
import com.netflix.spinnaker.clouddriver.openstack.model.OpenstackLoadBalancer
import com.netflix.spinnaker.clouddriver.openstack.model.OpenstackLoadBalancerSummary
import com.netflix.spinnaker.clouddriver.openstack.provider.view.OpenstackLoadBalancerProvider
import redis.clients.jedis.exceptions.JedisException
import spock.lang.Specification
import spock.lang.Unroll

@Unroll
class OpenstackLoadBalancerControllerSpec extends Specification {
  OpenstackLoadBalancerController controller
  OpenstackLoadBalancerProvider provider

  String account = 'test'
  String region = 'r1'

  def setup() {
    provider = Mock(OpenstackLoadBalancerProvider)
    controller = new OpenstackLoadBalancerController(provider)
  }

  def 'search for all load balancers'() {
    when:
    Set<OpenstackLoadBalancerSummary> result = controller.list()

    then:
    1 * provider.getLoadBalancers('*','*','*') >> lbs
    result.size() == lbs.size()
    if (result.size() > 0) {
      result.each {
        assert it.name != null
        assert it.id != null
        assert it.account != null
        assert it.region != null
      }
    }
    noExceptionThrown()

    where:
    lbs << [(0..1).collect { create(it) }.toSet(), [].toSet()]
  }

  def 'search for all load balancers - throw exception'() {
    given:
    Throwable throwable = new JedisException('exception')

    when:
    controller.list()

    then:
    1 * provider.getLoadBalancers('*','*','*') >> { throw throwable }
    Throwable thrownException = thrown(JedisException)
    thrownException == throwable
  }

  def 'get load balancer by account, region, name'() {
    given:
    String name = 'id0'

    when:
    Set<OpenstackLoadBalancer> result = controller.getDetailsInAccountAndRegionByName(account, region, name)

    then:
    1 * provider.getLoadBalancers(account, region, name) >> lbs
    result.size() == lbs.size()
    if (result.size() > 0) {
      assert result[0] == lbs[0]
    }
    noExceptionThrown()

    where:
    lbs << [[create(0)].toSet(), [].toSet()]
  }

  def 'get load balancer by account, region, name - throw exception'() {
    given:
    String name = 'id0'
    Throwable throwable = new JedisException('exception')

    when:
    controller.getDetailsInAccountAndRegionByName(account, region, name)

    then:
    1 * provider.getLoadBalancers(account, region, name) >> { throw throwable }
    Throwable thrownException = thrown(JedisException)
    thrownException == throwable
  }

  OpenstackLoadBalancer.View create(int i) {
    String account = 'test'
    String region = 'r1'
    String id = "id$i"
    String name = "name$i"
    String description = 'internal_port=8100'
    String status = 'up'
    String protocol = 'http'
    String algorithm = 'round_robin'
    String ip = '1.2.3.4'
    Integer externalPort = 80
    String subnet = "subnet$i"
    String network = "network$i"
    def healthMonitor = new OpenstackLoadBalancer.OpenstackHealthMonitor(id: "health$i", httpMethod: 'GET',
      maxRetries: 5, adminStateUp: 'UP', delay: 5, expectedCodes: '200')
    def serverGroups = [new LoadBalancerServerGroup(name: 'sg1', isDisabled: false,
      instances: [new LoadBalancerInstance(id: 'id', zone: "zone$i", health: [state:'up', zone: "zone$i"])])]
    new OpenstackLoadBalancer.View(account: account, region: region, id: id, name: name, description: description,
      status: status, algorithm: algorithm, ip: ip, subnetId: subnet, subnetName: subnet, networkId: network, networkName: network,
      healthMonitor: healthMonitor, serverGroups: serverGroups)
  }
}
