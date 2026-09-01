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

import software.amazon.awssdk.services.ec2.Ec2Client
import software.amazon.awssdk.services.ec2.model.DescribeInstancesRequest
import software.amazon.awssdk.services.ec2.model.DescribeInstancesResponse
import software.amazon.awssdk.services.ec2.model.Instance
import software.amazon.awssdk.services.ec2.model.InstanceState
import software.amazon.awssdk.services.ec2.model.Reservation
import software.amazon.awssdk.services.ec2.model.Tag
import software.amazon.awssdk.services.ec2.model.TerminateInstancesRequest
import com.netflix.spinnaker.clouddriver.aws.TestCredential
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.DetachInstancesAtomicOperation
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import com.netflix.spinnaker.credentials.CredentialsRepository
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
      1 * getAmazonEC2V2(test, "us-west-1") >> { amazonEC2USW }
      1 * getAmazonEC2V2(test, "us-east-1") >> { amazonEC2USE }
      0 * _
    }
    CredentialsRepository<NetflixAmazonCredentials> credentialsRepository = Stub(CredentialsRepository) {
      getAll() >> [test]
    }
    def agent = new CleanupDetachedInstancesAgent(amazonClientProvider, credentialsRepository)

    when:
    agent.run()

    then:
    1 * amazonEC2USW.terminateInstances({ TerminateInstancesRequest request ->
      request.instanceIds() == ["i-us-west-1_1", "i-us-west-1_2"]
    } as TerminateInstancesRequest)
    1 * amazonEC2USE.terminateInstances({ TerminateInstancesRequest request ->
      request.instanceIds() == ["i-us-east-1_1", "i-us-east-1_2"]
    } as TerminateInstancesRequest)
  }

  @Unroll
  void "should terminate only when explicitly tagged"() {
    expect:
    CleanupDetachedInstancesAgent.shouldTerminate(instance) == shouldTerminate

    where:
    instance                                                                                                 || shouldTerminate
    Instance.builder().build()                                                                               || false // not tagged for termination
    Instance.builder().tags(tag("unknown")).build()                                                          || false // not tagged for termination
    Instance.builder().tags(tag("spinnaker:PendingTermination")).build()                                     || true // pending termination and not in ASG
    Instance.builder()
      .state(InstanceState.builder().name("terminated").build())
      .tags(tag("spinnaker:PendingTermination")).build()                                                     || false // already terminated
    Instance.builder().tags(
      tag("spinnaker:PendingTermination"),
      tag("aws:autoscaling:groupName", "test-v000")
    ).build()                                                                                                 || false // still in ASG
  }

  private static Tag tag(String key, String value = null) {
    return Tag.builder().key(key).value(value).build()
  }

  private Ec2Client mockAmazonEC2(String region) {
    return Mock(Ec2Client) {
      1 * describeInstances(_) >> { DescribeInstancesRequest request ->
        assert request.filters().find { it.name() == "tag-key" && it.values() == [DetachInstancesAtomicOperation.TAG_PENDING_TERMINATION]}
        DescribeInstancesResponse.builder().reservations(Reservation.builder().instances([
          Instance.builder().tags(tag("spinnaker:PendingTermination")).instanceId("i-${region}_1").build(),
          Instance.builder().tags(tag("spinnaker:PendingTermination")).instanceId("i-${region}_2").build(),
          Instance.builder().instanceId("i-${region}_3").build(),
        ]).build()).build()
      }
      0 * _
    }
  }
}
