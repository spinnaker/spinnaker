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

import software.amazon.awssdk.awscore.exception.AwsErrorDetails
import software.amazon.awssdk.awscore.exception.AwsServiceException
import software.amazon.awssdk.services.elasticloadbalancing.model.CreateLoadBalancerListenersRequest
import software.amazon.awssdk.services.elasticloadbalancing.model.Listener
import software.amazon.awssdk.services.elasticloadbalancing.model.ListenerDescription
import software.amazon.awssdk.services.elasticloadbalancing.model.LoadBalancerDescription
import software.amazon.awssdk.services.elasticloadbalancing.ElasticLoadBalancingClient
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperationException
import spock.lang.Specification

class LoadBalancerUpsertHandlerSpec extends Specification {

  ElasticLoadBalancingClient loadBalancing = Mock()

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  def 'should rollback deleted listeners on existing loadbalancer when add listener fails'() {
    given:
    def oldListener = Listener.builder()
      .protocol('http')
      .loadBalancerPort(80)
      .instanceProtocol('http')
      .instancePort(80)
      .build()
    def loadBalancer = LoadBalancerDescription.builder()
      .loadBalancerName('theloadbalancingest-lb')
      .vpcId('vpc-1234')
      .listenerDescriptions([
        ListenerDescription.builder().listener(oldListener).build()
      ])
      .build()
    def listeners = [
      Listener.builder().protocol('https').loadBalancerPort(443).instanceProtocol('http').instancePort(80).build()
    ]

    when:
    LoadBalancerUpsertHandler.updateLoadBalancer(loadBalancing, loadBalancer, listeners, ['sg-1234'])

    then:
    AtomicOperationException e = thrown()
    1 * loadBalancing.applySecurityGroupsToLoadBalancer(_)
    1 * loadBalancing.deleteLoadBalancerListeners(_)
    1 * loadBalancing.createLoadBalancerListeners(CreateLoadBalancerListenersRequest.builder().loadBalancerName('theloadbalancingest-lb').listeners(listeners).build()) >> {
      throw AwsServiceException.builder()
        .message("Missing SSL certificate")
        .awsErrorDetails(AwsErrorDetails.builder().errorMessage("Missing SSL certificate").build())
        .build()
    }
    1 * loadBalancing.createLoadBalancerListeners(CreateLoadBalancerListenersRequest.builder().loadBalancerName('theloadbalancingest-lb').listeners([oldListener]).build())
  }

}
