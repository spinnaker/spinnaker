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
package com.netflix.spinnaker.keel.intents.processors

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.keel.clouddriver.ClouddriverService
import com.netflix.spinnaker.keel.clouddriver.model.Moniker
import com.netflix.spinnaker.keel.clouddriver.model.Network
import com.netflix.spinnaker.keel.clouddriver.model.SecurityGroup
import com.netflix.spinnaker.keel.intents.AmazonSecurityGroupSpec
import com.netflix.spinnaker.keel.intents.ApplicationIntent
import com.netflix.spinnaker.keel.intents.BaseApplicationSpec
import com.netflix.spinnaker.keel.intents.ReferenceSecurityGroupRule
import com.netflix.spinnaker.keel.intents.SecurityGroupIntent
import com.netflix.spinnaker.keel.intents.SecurityGroupSpec
import com.netflix.spinnaker.keel.intents.processors.converters.SecurityGroupConverter
import com.netflix.spinnaker.keel.tracing.TraceRepository
import spock.lang.Specification
import spock.lang.Subject

class SecurityGroupIntentProcessorSpec extends Specification {

  TraceRepository traceRepository = Mock()
  ClouddriverService clouddriverService = Mock() {
    listNetworks() >> {
      [
        aws: [
          new Network('aws', 'vpc-1', 'vpcName', 'test', 'us-west-2'),
          new Network('aws', 'vpc-2', 'vpcName', 'prod', 'us-west-2'),
          new Network('aws', 'vpc-3', 'vpcName', 'test', 'us-east-1'),
          new Network('aws', 'vpc-4', 'vpcName', 'test', 'eu-west-1'),
          new Network('aws', 'vpc-5', 'otherName', 'test', 'us-west-2')
        ] as Set
      ]
    }
  }
  ObjectMapper objectMapper = new ObjectMapper()
  SecurityGroupConverter converter = new SecurityGroupConverter(clouddriverService, objectMapper)

  @Subject subject = new SecurityGroupIntentProcessor(traceRepository, clouddriverService, objectMapper, converter)

  def 'should support SecurityGroupIntents'() {
    expect:
    !subject.supports(new ApplicationIntent(Mock(BaseApplicationSpec)))
    subject.supports(new SecurityGroupIntent(Mock(SecurityGroupSpec)))
  }

  def 'should upsert security group when missing'() {
    given:
    def intent = new SecurityGroupIntent(new AmazonSecurityGroupSpec(
      'keel',
      'keel',
      'aws',
      'test',
      ['us-west-2'] as Set,
      [] as Set,
      [] as Set,
      'vpcName',
      'app sg'
    ))

    when:
    def result = subject.converge(intent)

    then:
    1 * clouddriverService.getSecurityGroup(_, _, _, _, _) >> { null }
    result.orchestrations.size() == 1
    result.orchestrations[0].name == 'Upsert security group'
    result.orchestrations[0].application == 'keel'
    result.orchestrations[0].job[0]['type'] == 'upsertSecurityGroup'
  }

  def 'should upsert security group when present'() {
    given:
    def intent = new SecurityGroupIntent(new AmazonSecurityGroupSpec(
      'keel',
      'keel',
      'aws',
      'test',
      ['us-west-2'] as Set,
      [] as Set,
      [] as Set,
      'vpcName',
      'app sg'
    ))

    when:
    def result = subject.converge(intent)

    then:
    1 * clouddriverService.getSecurityGroup(_, _, _, _, _) >> {
      new SecurityGroup(
        "aws",
        "sg-1234",
        "keel",
        "app sg",
        "test",
        "us-west-2",
        "vpcName",
        [],
        new Moniker("test", null, null, null, null)
      )
    }
    result.orchestrations.size() == 1
    result.orchestrations[0].name == 'Upsert security group'
    result.orchestrations[0].application == 'keel'
    result.orchestrations[0].job[0]['type'] == 'upsertSecurityGroup'

  }

  def 'should skip operation if upstream groups are missing'() {
    given:
    def intent = new SecurityGroupIntent(new AmazonSecurityGroupSpec(
      'keel',
      'keel',
      'aws',
      'test',
      ['us-west-2'] as Set,
      [
        new ReferenceSecurityGroupRule(
          [] as SortedSet,
          "tcp",
          "gate"
        )
      ] as Set,
      [] as Set,
      'vpcName',
      'app sg'
    ))

    when:
    def result = subject.converge(intent)

    then:
    1 * clouddriverService.getSecurityGroup("test", "aws", "gate", "us-west-2") >> { null }
    result.orchestrations.isEmpty()
    result.reason == "Some upstream security groups are missing"
  }
}
