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

import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.model.AttachClassicLinkVpcRequest
import com.amazonaws.services.ec2.model.ClassicLinkInstance
import com.amazonaws.services.ec2.model.DescribeVpcClassicLinkResult
import com.amazonaws.services.ec2.model.Instance
import com.amazonaws.services.ec2.model.Tag
import com.netflix.spinnaker.config.AwsConfiguration
import com.netflix.spinnaker.clouddriver.aws.TestCredential
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import spock.lang.Shared
import spock.lang.Specification

import java.time.Clock
import java.time.Instant
import java.time.ZoneId

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
  def ec2 = Mock(AmazonEC2)
  def amazonClientProvider = Stub(AmazonClientProvider) {
    getAmazonEC2(_, _, _) >> ec2
  }

  def agent = buildAgent(test)

  @Shared
  Instant currentTime = Instant.now()

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
    1 * ec2.describeVpcClassicLink() >> new DescribeVpcClassicLinkResult()
    0 * _
  }

  def "should filter instances that havent been up long enough"() {
    given:
    Instance i = new Instance(launchTime: launchTime)

    expect:
    agent.isInstanceOldEnough(i) == expected

    where:
    launchTime                                                                                                                       | expected
    null                                                                                                                             | false
    new Date(currentTime.toEpochMilli())                                                                                             | false
    new Date(currentTime.minusMillis(ReconcileClassicLinkSecurityGroupsAgent.DEFAULT_REQUIRED_INSTANCE_LIFETIME).toEpochMilli())     | false
    new Date(currentTime.minusMillis(ReconcileClassicLinkSecurityGroupsAgent.DEFAULT_REQUIRED_INSTANCE_LIFETIME - 1).toEpochMilli()) | false
    new Date(currentTime.minusMillis(ReconcileClassicLinkSecurityGroupsAgent.DEFAULT_REQUIRED_INSTANCE_LIFETIME + 1).toEpochMilli()) | true
  }

  def "should add missing groups"() {
    when:
    agent.reconcileInstances(ec2, groups, classicLinkInstances)

    then:
    1 * ec2.attachClassicLinkVpc(_) >> { AttachClassicLinkVpcRequest req ->
      assert req.groups.sort() == groups.values().sort()
    }
    0 * _


    where:
    classicLinkVpcId = "vpc-1234"
    groups = ["nf-classiclink": "sg-1234", "foo": "sg-2345"]
    classicLinkInstances = [new ClassicLinkInstance().withInstanceId("i-1234").withVpcId(classicLinkVpcId).withTags(new Tag(ReconcileClassicLinkSecurityGroupsAgent.AUTOSCALING_TAG, "foo-v001"))]
  }

  def "should classiclink non ASG instance"() {
    when:
    agent.reconcileInstances(ec2, groups, classicLinkInstances)

    then:
    1 * ec2.attachClassicLinkVpc(_) >> { AttachClassicLinkVpcRequest req ->
      assert req.groups.sort() == groups.values().sort()
    }
    0 * _


    where:
    classicLinkVpcId = "vpc-1234"
    groups = ["nf-classiclink": "sg-1234"]
    classicLinkInstances = [new ClassicLinkInstance().withInstanceId("i-1234").withVpcId(classicLinkVpcId)]
  }

  def "should only include existing groups when classiclinking"() {
    when:
    agent.reconcileInstances(ec2, groups, classicLinkInstances)

    then:
    1 * ec2.attachClassicLinkVpc(_) >> { AttachClassicLinkVpcRequest req ->
      assert req.groups.sort() == groups.values().sort()
    }
    0 * _


    where:
    classicLinkVpcId = "vpc-1234"
    groups = ["nf-classiclink": "sg-1234", "foo": "sg-2345", "foo-bar-baz": "sg-3456"]
    classicLinkInstances = [new ClassicLinkInstance().withInstanceId("i-1234").withVpcId(classicLinkVpcId).withTags(new Tag(ReconcileClassicLinkSecurityGroupsAgent.AUTOSCALING_TAG, "foo-bar-baz-v001"))]
  }

  def "should not exceed maximum number of groups"() {
    when:
    defaults.maxClassicLinkSecurityGroups = maxGroups
    agent.reconcileInstances(ec2, groups, classicLinkInstances)

    then:
    1 * ec2.attachClassicLinkVpc(_) >> { AttachClassicLinkVpcRequest req ->
      assert req.groups == expectedGroups
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
    classicLinkInstances = [new ClassicLinkInstance().withInstanceId("i-1234").withVpcId(classicLinkVpcId).withTags(new Tag(ReconcileClassicLinkSecurityGroupsAgent.AUTOSCALING_TAG, "foo-bar-baz-v001"))]
  }
}
