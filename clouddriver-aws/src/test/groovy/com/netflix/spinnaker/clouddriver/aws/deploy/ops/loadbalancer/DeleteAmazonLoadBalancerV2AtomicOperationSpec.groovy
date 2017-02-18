/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.aws.deploy.ops.loadbalancer

import com.amazonaws.services.elasticloadbalancingv2.AmazonElasticLoadBalancing
import com.amazonaws.services.elasticloadbalancingv2.model.DeleteLoadBalancerRequest
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeLoadBalancersRequest
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeLoadBalancersResult
import com.amazonaws.services.elasticloadbalancingv2.model.LoadBalancer
import com.netflix.spinnaker.clouddriver.aws.deploy.description.DeleteAmazonLoadBalancerDescription
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import spock.lang.Specification
import spock.lang.Subject

class DeleteAmazonLoadBalancerV2AtomicOperationSpec extends Specification {
  private static final String ACCOUNT = "test"

  def credz = Stub(NetflixAmazonCredentials) {
    getName() >> ACCOUNT
  }
  def description = new DeleteAmazonLoadBalancerDescription(loadBalancerName: "foo--frontend", regions: ["us-east-1"], credentials: credz)

  @Subject
    op = new DeleteAmazonLoadBalancerV2AtomicOperation(description)

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  void "should perform deletion when invoked"() {
    setup:
    def loadBalancerArn = "foo:test"
    def loadBalancing = Mock(AmazonElasticLoadBalancing)
    def amazonClientProvider = Stub(AmazonClientProvider)
    amazonClientProvider.getAmazonElasticLoadBalancingV2(credz, _, true) >> loadBalancing
    op.amazonClientProvider = amazonClientProvider

    when:
    op.operate([])

    then:
    1 * loadBalancing.describeLoadBalancers(new DescribeLoadBalancersRequest(names: [description.loadBalancerName])) >> new DescribeLoadBalancersResult(loadBalancers: [ new LoadBalancer(loadBalancerArn: loadBalancerArn) ])
    1 * loadBalancing.deleteLoadBalancer(_) >> { DeleteLoadBalancerRequest req ->
      assert req.loadBalancerArn == loadBalancerArn
    }
    0 * _
  }
}
