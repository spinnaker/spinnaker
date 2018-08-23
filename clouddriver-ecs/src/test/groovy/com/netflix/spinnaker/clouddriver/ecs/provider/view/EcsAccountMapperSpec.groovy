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

import com.netflix.spinnaker.clouddriver.aws.security.NetflixAssumeRoleAmazonCredentials
import com.netflix.spinnaker.clouddriver.ecs.security.NetflixAssumeRoleEcsCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import spock.lang.Specification

class EcsAccountMapperSpec extends Specification {

  def accountCredentialsProvider = Mock(AccountCredentialsProvider)
  def awsAccount = Mock(NetflixAssumeRoleAmazonCredentials)
  def ecsAccount = Mock(NetflixAssumeRoleEcsCredentials)

  def 'should map an AWS account to its ECS account'() {
    given:
    awsAccount.name >> 'awsAccountNameHere'
    ecsAccount.name >> 'ecsAccountNameHere'
    ecsAccount.awsAccount >> awsAccount.name


    def accounts = [ ecsAccount, awsAccount ]
    accountCredentialsProvider.getAll() >> accounts

    def ecsAccountMapper = new EcsAccountMapper(accountCredentialsProvider)

    when:
    def retrievedEcsAccount = ecsAccountMapper.fromAwsAccountNameToEcs(awsAccount.name)
    def retrievedAwsAccount = ecsAccountMapper.fromEcsAccountNameToAws(ecsAccount.name)

    then:
    retrievedEcsAccount.name == ecsAccount.name
    retrievedAwsAccount.name == awsAccount.name
  }
}
