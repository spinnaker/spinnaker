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

package com.netflix.kato.deploy.aws.ops.loadbalancer

import com.amazonaws.auth.AWSCredentials
import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancing
import com.amazonaws.services.elasticloadbalancing.model.CreateLoadBalancerRequest
import com.netflix.kato.data.task.Task
import com.netflix.kato.data.task.TaskRepository
import com.netflix.kato.deploy.aws.StaticAmazonClients
import com.netflix.kato.deploy.aws.description.CreateAmazonLoadBalancerDescription
import com.netflix.kato.security.aws.AmazonCredentials
import spock.lang.Specification

class CreateAmazonLoadBalancerAtomicOperationSpec extends Specification {

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  void "operation builds appropriate request/response graph"() {
    setup:
    def mockClient = Mock(AmazonElasticLoadBalancing)
    StaticAmazonClients.metaClass.'static'.getAmazonElasticLoadBalancing = { AmazonCredentials credentials, String region ->
      mockClient
    }
    def description = new CreateAmazonLoadBalancerDescription(clusterName: "kato-main", availabilityZones: ["us-east-1": ["us-east-1a"]],
      listeners: [
        new CreateAmazonLoadBalancerDescription.Listener(
          externalProtocol: CreateAmazonLoadBalancerDescription.Listener.ListenerType.HTTP,
          externalPort: 80,
          internalPort: 8501
        )
      ],
      credentials: new AmazonCredentials(Mock(AWSCredentials), "bar")
    )
    def operation = new CreateAmazonLoadBalancerAtomicOperation(description)

    when:
    def result = operation.operate([])

    then:
    1 * mockClient.createLoadBalancer(_) >> { CreateLoadBalancerRequest request ->
      assert 1 == request.listeners.size()
      def listener = request.listeners.first()
      assert 8501 == listener.instancePort
      assert 80 == listener.loadBalancerPort
      assert "HTTP" == listener.protocol
      assert "HTTP" == listener.instanceProtocol
      def awsResponse = Mock(com.amazonaws.services.elasticloadbalancing.model.CreateLoadBalancerResult)
      awsResponse.getDNSName() >> "ok"
      awsResponse
    }
    result.loadBalancers."us-east-1".name == "kato-main-frontend"
    result.loadBalancers."us-east-1".dnsName == "ok"

  }
}
