/*
 * Copyright 2014 Netflix, Inc.
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
package com.netflix.spinnaker.kato.services

import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancing
import com.amazonaws.services.elasticloadbalancing.model.DeregisterInstancesFromLoadBalancerRequest
import com.amazonaws.services.elasticloadbalancing.model.Instance
import com.amazonaws.services.elasticloadbalancing.model.RegisterInstancesWithLoadBalancerRequest
import spock.lang.Specification
import spock.lang.Subject

class ElbServiceSpec extends Specification {

  def mockThrottleService = Mock(ThrottleService)
  def mockAmazonElasticLoadBalancing = Mock(AmazonElasticLoadBalancing)
  @Subject def elbService = new ElbService(mockThrottleService, mockAmazonElasticLoadBalancing)

  void 'should register instances with load balancer'() {
    when:
    elbService.registerInstancesWithLoadBalancer(["elb1", "elb2"], ["i1", "i2"])

    then:
    1 * mockAmazonElasticLoadBalancing.registerInstancesWithLoadBalancer(
      new RegisterInstancesWithLoadBalancerRequest("elb1", ["i1", "i2"].collect { new Instance(instanceId: it) }))
    0 * _

    then:
    1 * mockThrottleService.sleepMillis(250)

    then:
    1 * mockAmazonElasticLoadBalancing.registerInstancesWithLoadBalancer(
      new RegisterInstancesWithLoadBalancerRequest("elb2", ["i1", "i2"].collect { new Instance(instanceId: it) }))
  }

  void 'should deregister instances from load balancer'() {
    when:
    elbService.deregisterInstancesFromLoadBalancer(["elb1", "elb2"], ["i1", "i2"])

    then:
    1 * mockAmazonElasticLoadBalancing.deregisterInstancesFromLoadBalancer(
      new DeregisterInstancesFromLoadBalancerRequest("elb1", ["i1", "i2"].collect { new Instance(instanceId: it) }))
    0 * _

    then:
    1 * mockThrottleService.sleepMillis(250)

    then:
    1 * mockAmazonElasticLoadBalancing.deregisterInstancesFromLoadBalancer(
      new DeregisterInstancesFromLoadBalancerRequest("elb2", ["i1", "i2"].collect { new Instance(instanceId: it) }))
  }

  void 'should not act without load balancers'() {
    when:
    elbService.registerInstancesWithLoadBalancer([], ["i1", "i2"])

    then:
    0 * _
  }

  void 'should not act without instances'() {
    when:
    elbService.registerInstancesWithLoadBalancer(["elb1", "elb2"], [])

    then:
    0 * _
  }

}
