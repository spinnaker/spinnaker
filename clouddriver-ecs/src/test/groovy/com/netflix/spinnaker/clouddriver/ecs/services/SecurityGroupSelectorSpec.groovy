/*
 * Copyright 2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
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

package com.netflix.spinnaker.clouddriver.ecs.services

import com.google.common.collect.Sets
import com.netflix.spinnaker.clouddriver.aws.model.AmazonSecurityGroup
import com.netflix.spinnaker.clouddriver.aws.provider.view.AmazonSecurityGroupProvider
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import com.netflix.spinnaker.clouddriver.ecs.model.EcsSecurityGroup
import com.netflix.spinnaker.clouddriver.ecs.provider.view.AmazonPrimitiveConverter
import com.netflix.spinnaker.clouddriver.ecs.provider.view.EcsAccountMapper
import spock.lang.Specification

class SecurityGroupSelectorSpec extends Specification {

  public static final String ECS_ACCOUNT = 'ecsAccount'
  public static final String AWS_ACCOUNT = 'awsAccount'
  public static final String REGION = 'us-west-2'
  public static final String GOOD_SG_ID_1 = 'sg-aa123456'
  public static final String GOOD_SG_ID_2 = 'sg-bb123123'
  public static final String BAD_SG_ID_1 = 'sg-dd345345'
  public static final String BAD_SG_ID_2 = 'sg-cc233323'
  public static final String GOOD_VPC_ID = 'vpc-1'
  public static final String BAD_VPC_ID = 'vpc-2'
  public static final String SG_NAME_1 = 'hello'
  public static final String SG_NAME_2 = 'world'
  public static final String SG_NAME_3 = 'fizz'
  public static final String SG_NAME_4 = 'buzz'

  def amazonSecurityGroupProvider = Mock(AmazonSecurityGroupProvider)
  def amazonPrimitiveConverter = Mock(AmazonPrimitiveConverter)
  def accountMapper = Mock(EcsAccountMapper)
  def awsAccount = Mock(NetflixAmazonCredentials)

  def 'should find the right sg'() {
    given:
    def desiredSGIds = [GOOD_SG_ID_2, GOOD_SG_ID_1]
    def sgNamesToRetrieve = [SG_NAME_1, SG_NAME_2, SG_NAME_3]
    def vpcIdsToRetrieve = [GOOD_VPC_ID]

    def sg1 = new AmazonSecurityGroup(
      accountName: AWS_ACCOUNT,
      region: REGION,
      name: SG_NAME_1,
      vpcId: GOOD_VPC_ID,
      id: GOOD_SG_ID_1)

    def sg2 = new AmazonSecurityGroup(
      accountName: AWS_ACCOUNT,
      region: REGION,
      name: SG_NAME_2,
      vpcId: GOOD_VPC_ID,
      id: GOOD_SG_ID_2)

    // should be filtered out by VPC ID
    def sg3 = new AmazonSecurityGroup(
      accountName: AWS_ACCOUNT,
      region: REGION,
      name: SG_NAME_3,
      vpcId: BAD_VPC_ID,
      id: BAD_SG_ID_1)

    // should be filtered out by SG name
    def sg4 = new AmazonSecurityGroup(
      accountName: AWS_ACCOUNT,
      region: REGION,
      name: SG_NAME_4,
      vpcId: GOOD_VPC_ID,
      id: BAD_SG_ID_2)

    def ecsSG1 = new EcsSecurityGroup(GOOD_SG_ID_1, SG_NAME_1, GOOD_VPC_ID, null, null, AWS_ACCOUNT, null, REGION, null, null)

    def ecsSG2 = new EcsSecurityGroup(GOOD_SG_ID_2, SG_NAME_2, GOOD_VPC_ID, null, null, AWS_ACCOUNT, null, REGION, null, null)

    // should be filtered out by VPC ID
    def ecsSG3 = new EcsSecurityGroup(BAD_SG_ID_1, SG_NAME_3, BAD_VPC_ID, null, null, AWS_ACCOUNT, null, REGION, null, null)

    // should be filtered out by SG name
    def ecsSG4 = new EcsSecurityGroup(BAD_SG_ID_2, SG_NAME_4, GOOD_VPC_ID, null, null, AWS_ACCOUNT, null, REGION, null, null)

    awsAccount.name >> AWS_ACCOUNT

    accountMapper.fromEcsAccountNameToAws(ECS_ACCOUNT) >> awsAccount

    amazonSecurityGroupProvider.getAllByAccountAndRegion(_, _, _) >> [sg1, sg2, sg3, sg4]

    amazonPrimitiveConverter.convertToEcsSecurityGroup([sg1, sg2, sg3, sg4]) >> [ecsSG1, ecsSG2, ecsSG3, ecsSG4]

    def sgSelector = new SecurityGroupSelector(amazonSecurityGroupProvider, amazonPrimitiveConverter, accountMapper)


    when:
    def retrievedSGIds = sgSelector.resolveSecurityGroupNames(
      ECS_ACCOUNT,
      REGION,
      sgNamesToRetrieve,
      vpcIdsToRetrieve
    )
    retrievedSGIds.sort()
    desiredSGIds.sort()

    then:
    retrievedSGIds.containsAll(desiredSGIds)
    desiredSGIds.containsAll(retrievedSGIds)
  }
}
