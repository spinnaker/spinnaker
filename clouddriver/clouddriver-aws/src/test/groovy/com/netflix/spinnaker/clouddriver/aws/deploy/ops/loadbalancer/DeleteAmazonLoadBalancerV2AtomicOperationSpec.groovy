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

import software.amazon.awssdk.services.elasticloadbalancingv2.ElasticLoadBalancingV2Client
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DeleteListenerRequest
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DeleteLoadBalancerRequest
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DeleteTargetGroupRequest
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeListenersRequest
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeListenersResponse
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeLoadBalancerAttributesResponse
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeLoadBalancersRequest
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeLoadBalancersResponse
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeTargetGroupsRequest
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeTargetGroupsResponse
import software.amazon.awssdk.services.elasticloadbalancingv2.model.Listener
import software.amazon.awssdk.services.elasticloadbalancingv2.model.LoadBalancer
import software.amazon.awssdk.services.elasticloadbalancingv2.model.LoadBalancerAttribute
import software.amazon.awssdk.services.elasticloadbalancingv2.model.TargetGroup
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
    def listenerArn = "listener:arn"
    def targetGroupArn = "targetGroup:arn"
    def loadBalancing = Mock(ElasticLoadBalancingV2Client)
    def amazonClientProvider = Stub(AmazonClientProvider)
    amazonClientProvider.getElasticLoadBalancingV2Client(credz, _) >> loadBalancing
    op.amazonClientProvider = amazonClientProvider

    when:
    op.operate([])

    then:
    1 * loadBalancing.describeLoadBalancers(DescribeLoadBalancersRequest.builder().names([description.loadBalancerName]).build()) >>
      DescribeLoadBalancersResponse.builder().loadBalancers([LoadBalancer.builder().loadBalancerArn(loadBalancerArn).build()]).build()
    1 * loadBalancing.describeLoadBalancerAttributes(_) >>
      DescribeLoadBalancerAttributesResponse.builder().attributes([LoadBalancerAttribute.builder().key("deletion_protection.enabled").value("false").build()]).build()
    1 * loadBalancing.describeListeners(DescribeListenersRequest.builder().loadBalancerArn(loadBalancerArn).build()) >>
      DescribeListenersResponse.builder().listeners([Listener.builder().listenerArn(listenerArn).build()]).build()
    1 * loadBalancing.deleteListener(DeleteListenerRequest.builder().listenerArn(listenerArn).build())
    1 * loadBalancing.describeTargetGroups(DescribeTargetGroupsRequest.builder().loadBalancerArn(loadBalancerArn).build()) >>
      DescribeTargetGroupsResponse.builder().targetGroups([TargetGroup.builder().targetGroupArn(targetGroupArn).build()]).build()
    1 * loadBalancing.deleteTargetGroup(DeleteTargetGroupRequest.builder().targetGroupArn(targetGroupArn).build())
    1 * loadBalancing.deleteLoadBalancer(_) >> { DeleteLoadBalancerRequest req ->
      assert req.loadBalancerArn() == loadBalancerArn
    }
    0 * _
  }

  void "should abort if deletion protection is enabled"() {
    setup:
    def loadBalancerArn = "foo:test"
    def loadBalancing = Mock(ElasticLoadBalancingV2Client)
    def amazonClientProvider = Stub(AmazonClientProvider)
    amazonClientProvider.getElasticLoadBalancingV2Client(credz, _) >> loadBalancing
    op.amazonClientProvider = amazonClientProvider

    when:
    op.operate([])

    then:
    1 * loadBalancing.describeLoadBalancers(DescribeLoadBalancersRequest.builder().names([description.loadBalancerName]).build()) >>
      DescribeLoadBalancersResponse.builder().loadBalancers([LoadBalancer.builder().loadBalancerArn(loadBalancerArn).loadBalancerName('test').build()]).build()
    1 * loadBalancing.describeLoadBalancerAttributes(_) >>
      DescribeLoadBalancerAttributesResponse.builder().attributes([LoadBalancerAttribute.builder().key("deletion_protection.enabled").value("true").build()]).build()
    0 * _
    DeleteAmazonLoadBalancerV2AtomicOperation.DeletionProtectionEnabledException ex = thrown()
    ex.message == "Load Balancer test has deletion protection enabled. Aborting delete operation."
  }
}
