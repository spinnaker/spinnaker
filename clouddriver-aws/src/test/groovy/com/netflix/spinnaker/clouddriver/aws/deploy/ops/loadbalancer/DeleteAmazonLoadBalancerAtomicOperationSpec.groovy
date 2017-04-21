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

package com.netflix.spinnaker.clouddriver.aws.deploy.ops.loadbalancer

import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancing
import com.amazonaws.services.elasticloadbalancing.model.DeleteLoadBalancerRequest
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.loadbalancer.DeleteAmazonLoadBalancerAtomicOperation
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.aws.deploy.description.DeleteAmazonLoadBalancerDescription
import spock.lang.Specification
import spock.lang.Subject

class DeleteAmazonLoadBalancerAtomicOperationSpec extends Specification {
  private static final String ACCOUNT = "test"

  def credz = Stub(NetflixAmazonCredentials) {
    getName() >> ACCOUNT
  }
  def description = new DeleteAmazonLoadBalancerDescription(loadBalancerName: "foo--frontend", regions: ["us-east-1"], credentials: credz)

  @Subject
    op = new DeleteAmazonLoadBalancerAtomicOperation(description)

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  void "should perform deletion when invoked"() {
    setup:
    def loadBalancing = Mock(AmazonElasticLoadBalancing)
    def amazonClientProvider = Stub(AmazonClientProvider)
    amazonClientProvider.getAmazonElasticLoadBalancing(credz, _, true) >> loadBalancing
    op.amazonClientProvider = amazonClientProvider

    when:
    op.operate([])

    then:
    1 * loadBalancing.deleteLoadBalancer(_) >> { DeleteLoadBalancerRequest req ->
      assert req.loadBalancerName == description.loadBalancerName
    }
  }
}
