/*
 * Copyright 2017 Netflix, Inc.
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
package com.netflix.spinnaker.clouddriver.aws.deploy.handlers

import com.amazonaws.AmazonServiceException
import com.amazonaws.services.elasticloadbalancing.model.CreateLoadBalancerListenersRequest
import com.amazonaws.services.elasticloadbalancing.model.Listener
import com.amazonaws.services.elasticloadbalancing.model.ListenerDescription
import com.amazonaws.services.elasticloadbalancing.model.LoadBalancerDescription
import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancing
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperationException
import spock.lang.Specification

class LoadBalancerUpsertHandlerSpec extends Specification {

  AmazonElasticLoadBalancing loadBalancing = Mock()

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  def 'should rollback deleted listeners on existing loadbalancer when add listener fails'() {
    given:
    def oldListener = new Listener(
      protocol: 'http',
      loadBalancerPort: 80,
      instanceProtocol: 'http',
      instancePort: 80
    )
    def loadBalancer = new LoadBalancerDescription(
      loadBalancerName: 'theloadbalancingest-lb',
      vPCId: 'vpc-1234',
      listenerDescriptions: [
        new ListenerDescription(listener: oldListener)
      ]
    )
    def listeners = [
      new Listener(protocol: 'https', loadBalancerPort: 443, instanceProtocol: 'http', instancePort: 80)
    ]

    when:
    LoadBalancerUpsertHandler.updateLoadBalancer(loadBalancing, loadBalancer, listeners, ['sg-1234'])

    then:
    AtomicOperationException e = thrown()
    1 * loadBalancing.applySecurityGroupsToLoadBalancer(_)
    1 * loadBalancing.deleteLoadBalancerListeners(_)
    1 * loadBalancing.createLoadBalancerListeners(new CreateLoadBalancerListenersRequest('theloadbalancingest-lb', listeners)) >> {
      throw new AmazonServiceException("Missing SSL certificate")
    }
    1 * loadBalancing.createLoadBalancerListeners(new CreateLoadBalancerListenersRequest('theloadbalancingest-lb', [oldListener]))
  }

}
