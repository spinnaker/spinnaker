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
package com.netflix.spinnaker.keel.intents.processors.converters

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.keel.clouddriver.ClouddriverService
import com.netflix.spinnaker.keel.clouddriver.model.Moniker
import com.netflix.spinnaker.keel.clouddriver.model.Network
import com.netflix.spinnaker.keel.clouddriver.model.SecurityGroup
import com.netflix.spinnaker.keel.intents.AmazonSecurityGroupSpec
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject

class SecurityGroupConverterSpec extends Specification {

  @Shared
  ObjectMapper objectMapper = new ObjectMapper()

  ClouddriverService clouddriverService = Mock()

  @Subject
  def subject = new SecurityGroupConverter(clouddriverService, objectMapper)

  def 'should convert spec to system state'() {
    given:
    def spec = new AmazonSecurityGroupSpec(
      'keel',
      'keel',
      'aws',
      'test',
      ['us-west-2', 'us-east-1'] as Set,
      [] as Set,
      [] as Set,
      'vpcName',
      'application sg'
    )

    when:
    def result = subject.convertToState(spec)

    then:
    clouddriverService.listNetworks() >> {
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
    result.size() == 2
    result.first().with {
      it.type == 'aws'
      it.name == 'keel'
      it.description == 'application sg'
      it.accountName == 'test'
      it.region == 'us-west-2'
      it.vpcId == 'vpcName'
      it.inboundRules == []
    }
    result.last().with {
      it.name == 'keel'
      it.region == 'us-east-1'
    }
  }

  def 'should convert system state to spec'() {
    given:
    def state = [
      new SecurityGroup(
        'aws',
        'sg-1234',
        'keel',
        'application sg',
        'test',
        'us-west-2',
        'vpc-1234',
        [],
        new Moniker(
          'keel',
          'keel',
          null,
          null,
          null
        )
      ),
      new SecurityGroup(
        'aws',
        'sg-1235',
        'keel',
        'application sg',
        'test',
        'us-east-1',
        'vpc-1235',
        [],
        new Moniker(
          'keel',
          'keel',
          null,
          null,
          null
        )
      )
    ] as Set

    when:
    def result = subject.convertFromState(state)

    then:
    clouddriverService.listNetworks() >> { [
      aws: [
        new Network('aws', 'vpc-1234', 'vpcName', 'test', 'us-west-2'),
        new Network('aws', 'vpc-1235', 'vpcName', 'test', 'us-east-1')
      ] as Set
    ] }
    result == new AmazonSecurityGroupSpec(
      'keel',
      'keel',
      'aws',
      'test',
      ['us-west-2', 'us-east-1'] as Set,
      [] as Set,
      [] as Set,
      'vpcName',
      'application sg'
    )
  }

  def 'should convert spec to orchestration job'() {
    given:
    def spec = new AmazonSecurityGroupSpec(
      'keel',
      'keel',
      'aws',
      'test',
      ['us-west-2', 'us-east-1'] as Set,
      [] as Set,
      [] as Set,
      'vpcName',
      'app sg'
    )

    when:
    def result = subject.convertToJob(spec)

    then:
    result.size() == 1
    result[0].application == 'keel'
    result[0].cloudProvider == 'aws'
    result[0].regions == ['us-west-2', 'us-east-1'] as Set
    result[0].vpcId == 'vpcName'
    result[0].description == 'app sg'
    result[0].securityGroupIngress == [] as Set
    result[0].ipIngress == []
    result[0].accountName == 'test'
  }
}
