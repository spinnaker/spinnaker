/*
 * Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
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

package com.netflix.spinnaker.clouddriver.ecs.provider.view

import com.netflix.spinnaker.clouddriver.aws.model.AmazonSecurityGroup
import com.netflix.spinnaker.clouddriver.aws.provider.view.AmazonSecurityGroupProvider
import com.netflix.spinnaker.clouddriver.ecs.model.EcsSecurityGroup
import com.netflix.spinnaker.clouddriver.ecs.security.NetflixECSCredentials
import spock.lang.Specification
import spock.lang.Subject

class EcsSecurityGroupProviderSpec extends Specification {

  public static final String ECS_ACCOUNT = 'ecsAccount'
  public static final String AWS_ACCOUNT = 'awsAccount'
  public static final String REGION = 'us-west-2'
  public static final String SG_ID_1 = 'sg-aa123456'
  public static final String SG_ID_2 = 'sg-bb123123'
  public static final String VPC_ID = 'vpc-1'
  public static final String SG_NAME_1 = 'hello'
  public static final String SG_NAME_2 = 'world'

  def accountMapper = Mock(EcsAccountMapper)
  def ecsCreds = Mock(NetflixECSCredentials)
  def securityGroupProvider = Mock(AmazonSecurityGroupProvider)
  def primitiveConverter = new AmazonPrimitiveConverter(accountMapper)

  @Subject
  def provider = new EcsSecurityGroupProvider(primitiveConverter, securityGroupProvider, accountMapper)

  def sg1 = new AmazonSecurityGroup(
          accountName: AWS_ACCOUNT,
          region: REGION,
          name: SG_NAME_1,
          vpcId: VPC_ID,
          id: SG_ID_1)

  def sg2 = new AmazonSecurityGroup(
          accountName: AWS_ACCOUNT,
          region: REGION,
          name: SG_NAME_2,
          vpcId: VPC_ID,
          id: SG_ID_2)

  def ecsSg1 = new EcsSecurityGroup(
          SG_ID_1,
          SG_NAME_1,
          VPC_ID,
          null,
          null,
          ECS_ACCOUNT,
          null,
          REGION,
          null,
          null
  )

  def ecsSg2 = new EcsSecurityGroup(
          SG_ID_2,
          SG_NAME_2,
          VPC_ID,
          null,
          null,
          ECS_ACCOUNT,
          null,
          REGION,
          null,
          null
  )

  def setup() {
    ecsCreds.getName() >> ECS_ACCOUNT
    accountMapper.fromEcsAccountNameToAwsAccountName(ECS_ACCOUNT) >> AWS_ACCOUNT
    accountMapper.fromAwsAccountNameToEcs(AWS_ACCOUNT) >> ecsCreds
  }

  def 'should get all security groups from the AWS provider and convert them to ECS security groups'() {
    given:
    securityGroupProvider.getAll(true) >> [sg1, sg2]

    when:
    def ecsSGs = provider.getAll(true)

    then:
    ecsSGs.sort() == [ecsSg1, ecsSg2].sort()
  }

  def 'should get all security groups for region from the AWS provider and convert them to ECS security groups'() {
    given:
    securityGroupProvider.getAllByRegion(true, REGION) >> [sg1, sg2]

    when:
    def ecsSGs = provider.getAllByRegion(true, REGION)

    then:
    ecsSGs.sort() == [ecsSg1, ecsSg2].sort()
  }

  def 'should get all security groups from the AWS provider for the AWS account associated with the ECS account'() {
    given:
    securityGroupProvider.getAllByAccount(true, AWS_ACCOUNT) >> [sg1, sg2]

    when:
    def ecsSGs = provider.getAllByAccount(true, ECS_ACCOUNT)

    then:
    ecsSGs.sort() == [ecsSg1, ecsSg2].sort()
  }

  def 'should get all security groups from the AWS provider for the AWS account associated with the ECS account and security group name'() {
    given:
    securityGroupProvider.getAllByAccountAndName(true, AWS_ACCOUNT, SG_NAME_1) >> [sg1]

    when:
    def ecsSGs = provider.getAllByAccountAndName(true, ECS_ACCOUNT, SG_NAME_1)

    then:
    ecsSGs.sort() == [ecsSg1].sort()
  }

  def 'should get all security groups from the AWS provider for the AWS account associated with the ECS account and region'() {
    given:
    securityGroupProvider.getAllByAccountAndRegion(true, AWS_ACCOUNT, REGION) >> [sg1, sg2]

    when:
    def ecsSGs = provider.getAllByAccountAndRegion(true, ECS_ACCOUNT, REGION)

    then:
    ecsSGs.sort() == [ecsSg1, ecsSg2].sort()
  }

  def 'should get security group from the AWS provider by AWS account name, region, security group name, and VPC ID'() {
    given:
    securityGroupProvider.get(AWS_ACCOUNT, REGION, SG_NAME_1, VPC_ID) >> sg1

    when:
    def ecsSG = provider.get(ECS_ACCOUNT, REGION, SG_NAME_1, VPC_ID)

    then:
    ecsSG == ecsSg1
  }
}
