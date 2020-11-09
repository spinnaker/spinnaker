/*
 * Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License
 * for the specific language governing permissions and limitations under
 * the License.
 */

package com.netflix.spinnaker.clouddriver.ecs.security

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.clouddriver.aws.AmazonCloudProvider
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAssumeRoleAmazonCredentials
import com.netflix.spinnaker.clouddriver.ecs.TestCredential
import com.netflix.spinnaker.clouddriver.ecs.provider.view.EcsAccountMapper
import com.netflix.spinnaker.credentials.CompositeCredentialsRepository
import com.netflix.spinnaker.credentials.CredentialsRepository
import com.netflix.spinnaker.credentials.definition.CredentialsParser
import spock.lang.Specification

class EcsCredentialsParserSpec extends Specification{

  def credOne = TestCredential.named("one")

  def 'should parse credentials'() {
    given:
    def credJson = [
      name: "one-ecs",
      environment: "one",
      accountType: "one",
      accountId: "123456789012" + "one",
      defaultKeyPair: 'default-keypair',
      regions: [[name: 'us-east-1', availabilityZones: ['us-east-1b', 'us-east-1c', 'us-east-1d']],
                [name: 'us-west-1', availabilityZones: ["us-west-1a", "us-west-1b"]]],
      assumeRole: "oneRole",
      sessionName: "sessionOne"
    ]
    def assumeRoleCred = new ObjectMapper().convertValue(credJson, NetflixAssumeRoleAmazonCredentials)

    def compositeCredentialsRepository = Mock(CompositeCredentialsRepository)
    def parser = Mock(CredentialsParser)

    def ecsCredentialsParser = new EcsCredentialsParser<NetflixECSCredentials>(
      compositeCredentialsRepository, parser
    )
    def account = new ECSCredentialsConfig.Account(){{
        setName("one-ecs")
        setAwsAccount("one")
      }}

    when:
    def response = ecsCredentialsParser.parse(account)

    then:
    1 * parser.parse({it.getName() == "one-ecs" } ) >> assumeRoleCred
    1 * compositeCredentialsRepository.getCredentials("one", AmazonCloudProvider.ID) >> credOne
//    ecsAccountMapper.fromAwsAccountNameToEcsAccountName("one") == "one-ecs"
    response.getName() == "one-ecs"
  }
}
