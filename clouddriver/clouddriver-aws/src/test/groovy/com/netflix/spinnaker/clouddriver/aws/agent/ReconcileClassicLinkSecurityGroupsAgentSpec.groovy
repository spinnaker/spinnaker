/*
 * Copyright 2016 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.aws.agent

import software.amazon.awssdk.services.ec2.Ec2Client
import software.amazon.awssdk.services.ec2.model.AttachClassicLinkVpcRequest
import software.amazon.awssdk.services.ec2.model.ClassicLinkInstance
import software.amazon.awssdk.services.ec2.model.DescribeVpcClassicLinkResponse
import software.amazon.awssdk.services.ec2.model.Instance
import software.amazon.awssdk.services.ec2.model.Tag
import com.netflix.spinnaker.config.AwsConfiguration
import com.netflix.spinnaker.clouddriver.aws.TestCredential
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import spock.lang.Shared
import spock.lang.Specification

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * ReconcileClassicLinkSecurityGroupsAgentSpec.
 */
class ReconcileClassicLinkSecurityGroupsAgentSpec extends Specification {

  def prod = TestCredential.named("prod")
  def test = TestCredential.named("test")
  def defaults = new AwsConfiguration.DeployDefaults(
    classicLinkSecurityGroupName: "nf-classiclink",
    reconcileClassicLinkSecurityGroups: AwsConfiguration.DeployDefaults.ReconcileMode.MODIFY,
    reconcileClassicLinkAccounts: ["test"],
    addAppGroupsToClassicLink: true
  )
  def ec2 = Mock(Ec2Client)
  def amazonClientProvider = Stub(AmazonClientProvider) {
    getAmazonEC2V2(_, _) >> ec2
  }

  def agent = buildAgent(test)

  // Truncate to milliseconds so equality checks against a fixed clock aren't
  // thrown off by nanosecond precision.
  @Shared
  Instant currentTime = Instant.now().truncatedTo(ChronoUnit.MILLIS)

  private ReconcileClassicLinkSecurityGroupsAgent buildAgent(NetflixAmazonCredentials account) {
    return new ReconcileClassicLinkSecurityGroupsAgent(
      amazonClientProvider,
      account ?: test,
      "us-east-1",
      defaults,
      ReconcileClassicLinkSecurityGroupsAgent.DEFAULT_POLL_INTERVAL_MILLIS,
      ReconcileClassicLinkSecurityGroupsAgent.DEFAULT_TIMEOUT_MILLIS,
      ReconcileClassicLinkSecurityGroupsAgent.DEFAULT_REQUIRED_INSTANCE_LIFETIME,
      Clock.fixed(currentTime, ZoneId.of("UTC")))

  }


  def "should noop if reconcile is turned off"() {
    given:
    defaults.reconcileClassicLinkSecurityGroups = AwsConfiguration.DeployDefaults.ReconcileMode.NONE

    when:
    agent.run()

    then:
    0 * _
  }

  def "should noop if account not set for reconcile mode"() {
    given:
    agent = buildAgent(prod)

    when:
    agent.run()

    then:
    0 * _
  }

  def "should noop if no classic linked vpc"() {
    when:
    agent.run()

    then:
    1 * ec2.describeVpcClassicLink() >> DescribeVpcClassicLinkResponse.builder().build()
    0 * _
  }

  def "should filter instances that havent been up long enough"() {
    given:
    Instance i = Instance.builder().launchTime(launchTime).build()

    expect:
    agent.isInstanceOldEnough(i) == expected

    where:
    launchTime                                                                                     | expected
    null                                                                                            | false
    currentTime                                                                                     | false
    currentTime.minusMillis(ReconcileClassicLinkSecurityGroupsAgent.DEFAULT_REQUIRED_INSTANCE_LIFETIME)     | false
    currentTime.minusMillis(ReconcileClassicLinkSecurityGroupsAgent.DEFAULT_REQUIRED_INSTANCE_LIFETIME - 1) | false
    currentTime.minusMillis(ReconcileClassicLinkSecurityGroupsAgent.DEFAULT_REQUIRED_INSTANCE_LIFETIME + 1) | true
  }

  def "should add missing groups"() {
    when:
    agent.reconcileInstances(ec2, groups, classicLinkInstances)

    then:
    1 * ec2.attachClassicLinkVpc(_) >> { AttachClassicLinkVpcRequest req ->
      assert req.groups().sort(false) == groups.values().sort()
    }
    0 * _


    where:
    classicLinkVpcId = "vpc-1234"
    groups = ["nf-classiclink": "sg-1234", "foo": "sg-2345"]
    classicLinkInstances = [ClassicLinkInstance.builder().instanceId("i-1234").vpcId(classicLinkVpcId).tags(Tag.builder().key(ReconcileClassicLinkSecurityGroupsAgent.AUTOSCALING_TAG).value("foo-v001").build()).build()]
  }

  def "should classiclink non ASG instance"() {
    when:
    agent.reconcileInstances(ec2, groups, classicLinkInstances)

    then:
    1 * ec2.attachClassicLinkVpc(_) >> { AttachClassicLinkVpcRequest req ->
      assert req.groups().sort(false) == groups.values().sort()
    }
    0 * _


    where:
    classicLinkVpcId = "vpc-1234"
    groups = ["nf-classiclink": "sg-1234"]
    classicLinkInstances = [ClassicLinkInstance.builder().instanceId("i-1234").vpcId(classicLinkVpcId).build()]
  }

  def "should only include existing groups when classiclinking"() {
    when:
    agent.reconcileInstances(ec2, groups, classicLinkInstances)

    then:
    1 * ec2.attachClassicLinkVpc(_) >> { AttachClassicLinkVpcRequest req ->
      assert req.groups().sort(false) == groups.values().sort()
    }
    0 * _


    where:
    classicLinkVpcId = "vpc-1234"
    groups = ["nf-classiclink": "sg-1234", "foo": "sg-2345", "foo-bar-baz": "sg-3456"]
    classicLinkInstances = [ClassicLinkInstance.builder().instanceId("i-1234").vpcId(classicLinkVpcId).tags(Tag.builder().key(ReconcileClassicLinkSecurityGroupsAgent.AUTOSCALING_TAG).value("foo-bar-baz-v001").build()).build()]
  }

  def "should not exceed maximum number of groups"() {
    when:
    defaults.maxClassicLinkSecurityGroups = maxGroups
    agent.reconcileInstances(ec2, groups, classicLinkInstances)

    then:
    1 * ec2.attachClassicLinkVpc(_) >> { AttachClassicLinkVpcRequest req ->
      assert req.groups() == expectedGroups
    }
    0 * _


    where:
    maxGroups | expectedGroups
    1         | ["sg-1234"]
    2         | ["sg-1234", "sg-2345"]
    3         | ["sg-1234", "sg-2345", "sg-3456"]
    4         | ["sg-1234", "sg-2345", "sg-3456", "sg-4567"]
    5         | ["sg-1234", "sg-2345", "sg-3456", "sg-4567"]
    classicLinkVpcId = "vpc-1234"
    groups = ["nf-classiclink": "sg-1234", "foo": "sg-2345", "foo-bar": "sg-3456", "foo-bar-baz": "sg-4567"]
    classicLinkInstances = [ClassicLinkInstance.builder().instanceId("i-1234").vpcId(classicLinkVpcId).tags(Tag.builder().key(ReconcileClassicLinkSecurityGroupsAgent.AUTOSCALING_TAG).value("foo-bar-baz-v001").build()).build()]
  }
}
