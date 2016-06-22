/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.aws.agent

import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.model.DescribeInstancesRequest
import com.amazonaws.services.ec2.model.DescribeInstancesResult
import com.amazonaws.services.ec2.model.Instance
import com.amazonaws.services.ec2.model.InstanceState
import com.amazonaws.services.ec2.model.Reservation
import com.amazonaws.services.ec2.model.Tag
import com.amazonaws.services.ec2.model.TerminateInstancesRequest
import com.netflix.spinnaker.clouddriver.aws.TestCredential
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.DetachInstancesAtomicOperation
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

class CleanupDetachedInstancesAgentSpec extends Specification {
  @Shared
  def test = TestCredential.named('test')

  void "should run across all regions/accounts and terminate in each"() {
    given:
    def amazonEC2USW = mockAmazonEC2("us-west-1")
    def amazonEC2USE = mockAmazonEC2("us-east-1")

    def amazonClientProvider = Mock(AmazonClientProvider) {
      1 * getAmazonEC2(test, "us-west-1", true) >> { amazonEC2USW }
      1 * getAmazonEC2(test, "us-east-1", true) >> { amazonEC2USE }
      0 * _
    }

    def accountCredentialsRepository = Mock(AccountCredentialsRepository) {
      1 * getAll() >> [test]
      0 * _
    }
    def agent = new CleanupDetachedInstancesAgent(amazonClientProvider, accountCredentialsRepository)

    when:
    agent.run()

    then:
    1 * amazonEC2USW.terminateInstances({ TerminateInstancesRequest request ->
      request.instanceIds == ["i-us-west-1_1", "i-us-west-1_2"]
    } as TerminateInstancesRequest)
    1 * amazonEC2USE.terminateInstances({ TerminateInstancesRequest request ->
      request.instanceIds == ["i-us-east-1_1", "i-us-east-1_2"]
    } as TerminateInstancesRequest)
  }

  @Unroll
  void "should terminate only when explicitly tagged"() {
    expect:
    CleanupDetachedInstancesAgent.shouldTerminate(instance) == shouldTerminate

    where:
    instance                                                         || shouldTerminate
    new Instance()                                                   || false // not tagged for termination
    new Instance().withTags(new Tag("unknown"))                      || false // not tagged for termination
    new Instance().withTags(new Tag("spinnaker:PendingTermination")) || true // pending termination and not in ASG
    new Instance()
      .withState(new InstanceState().withName("terminated"))
      .withTags(new Tag("spinnaker:PendingTermination"))             || false // already terminated
    new Instance().withTags(
      new Tag("spinnaker:PendingTermination"),
      new Tag("aws:autoscaling:groupName", "test-v000")
    )                                                                || false // still in ASG
  }

  private AmazonEC2 mockAmazonEC2(String region) {
    return Mock(AmazonEC2) {
      1 * describeInstances(_) >> { DescribeInstancesRequest request ->
        assert request.filters.find { it.name == "tag-key" && it.values == [DetachInstancesAtomicOperation.TAG_PENDING_TERMINATION]}
        new DescribeInstancesResult().withReservations(new Reservation().withInstances([
          new Instance().withTags(new Tag("spinnaker:PendingTermination")).withInstanceId("i-${region}_1"),
          new Instance().withTags(new Tag("spinnaker:PendingTermination")).withInstanceId("i-${region}_2"),
          new Instance().withInstanceId("i-${region}_3"),
        ]))
      }
      0 * _
    }
  }
}
