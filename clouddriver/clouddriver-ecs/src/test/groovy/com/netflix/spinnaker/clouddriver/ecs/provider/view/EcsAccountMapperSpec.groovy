/*
 * Copyright 2018 Lookout, Inc.
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

package com.netflix.spinnaker.clouddriver.ecs.provider.view

import com.netflix.spinnaker.clouddriver.aws.AmazonCloudProvider
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import com.netflix.spinnaker.clouddriver.ecs.security.ECSCredentialsConfig
import com.netflix.spinnaker.clouddriver.ecs.security.NetflixAssumeRoleEcsCredentials
import com.netflix.spinnaker.credentials.CompositeCredentialsRepository
import com.netflix.spinnaker.credentials.CredentialsRepository
import spock.lang.Specification

class EcsAccountMapperSpec extends Specification {
  def awsAccountName = "awsAccountNameHere"
  def ecsAccountName = "ecsAccountNameHere"
  def awsAccount = Mock(NetflixAmazonCredentials) {
    getName() >> awsAccountName
  }
  def ecsAccount = Mock(NetflixAssumeRoleEcsCredentials) {
    getName() >> ecsAccountName
    getAwsAccount() >> awsAccountName
  }
  def credentialsRepository = Mock(CredentialsRepository) {
    getOne(ecsAccountName) >> ecsAccount
  }
  def compositeCredentialsRepository = Mock(CompositeCredentialsRepository) {
    getCredentials(awsAccountName, AmazonCloudProvider.ID) >> awsAccount
  }

  def 'should map an AWS account to its ECS account'() {
    given:
    def ecsAccountMapper = new EcsAccountMapper(credentialsRepository, compositeCredentialsRepository)

    ecsAccountMapper.addMapEntry(ecsAccount)

    when:
    def retrievedEcsAccount = ecsAccountMapper.fromAwsAccountNameToEcs(awsAccount.name)
    def retrievedAwsAccount = ecsAccountMapper.fromEcsAccountNameToAws(ecsAccount.name)

    then:
    retrievedEcsAccount.name == ecsAccount.name
    retrievedAwsAccount.name == awsAccount.name
  }

  def 'should map an AWS account name to its ECS account name'() {
    given:
    def ecsAccountMapper = new EcsAccountMapper(credentialsRepository, compositeCredentialsRepository)
    ecsAccountMapper.addMapEntry(ecsAccount)

    when:
    def retrievedEcsAccount = ecsAccountMapper.fromAwsAccountNameToEcsAccountName(awsAccount.name)
    def retrievedAwsAccount = ecsAccountMapper.fromEcsAccountNameToAwsAccountName(ecsAccount.name)

    then:
    retrievedEcsAccount == ecsAccount.name
    retrievedAwsAccount == awsAccount.name
  }

  def 'should remove AWS and ECS accounts'() {
    given:
    def ecsAccountMapper = new EcsAccountMapper(credentialsRepository, compositeCredentialsRepository)
    ecsAccountMapper.addMapEntry(ecsAccount)

    when:
    ecsAccountMapper.removeMapEntry(ecsAccountName)

    then:
    !ecsAccountMapper.ecsCredentialsMap.containsKey(ecsAccountName)
    !ecsAccountMapper.awsCredentialsMap.containsKey(awsAccount)
  }

  def 'should return null if provided name is invalid'() {
    given:
    def ecsAccountMapper = new EcsAccountMapper(credentialsRepository, compositeCredentialsRepository)

    when:
    def result = ecsAccountMapper.fromAwsAccountNameToEcs("invalid")

    then:
    result == null
  }
}
