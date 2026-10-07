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

package com.netflix.spinnaker.clouddriver.aws.deploy.ops.securitygroup

import software.amazon.awssdk.services.ec2.Ec2Client
import software.amazon.awssdk.services.ec2.model.*
import com.netflix.spinnaker.clouddriver.aws.deploy.description.UpsertSecurityGroupDescription
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.securitygroup.SecurityGroupLookupFactory.SecurityGroupUpdater
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import com.netflix.spinnaker.credentials.CredentialsRepository
import spock.lang.Specification
import spock.lang.Subject

class SecurityGroupLookupSpec extends Specification {

  def amazonEC2 = Mock(Ec2Client)
  def amazonClientProvider = Stub(AmazonClientProvider) {
    getAmazonEC2V2(_, "us-east-1") >> amazonEC2
  }
  def accountCredentialsRepository = Stub(CredentialsRepository) {
    getAll() >> [
      Stub(NetflixAmazonCredentials) {
        getName() >> "test"
        getAccountId() >> "id-test"
      },
      Stub(NetflixAmazonCredentials) {
        getName() >> "prod"
        getAccountId() >> "id-prod"
      }
    ]
  }

  def securityGroupLookupFactory = new SecurityGroupLookupFactory(amazonClientProvider,
    accountCredentialsRepository)

  @Subject
  def securityGroupLookup = securityGroupLookupFactory.getInstance("us-east-1")

  void "should create security group"() {
    when:
    final result = securityGroupLookup.createSecurityGroup(
      new UpsertSecurityGroupDescription(
        credentials: Stub(NetflixAmazonCredentials) {
          getName() >> "test"
        },
        vpcId: "vpc-1",
        name: "wideOpen",
        description: "desc",
        securityGroupIngress: []
      )
    )

    then:
    1 * amazonEC2.createSecurityGroup(CreateSecurityGroupRequest.builder()
      .groupName("wideOpen")
      .description("desc")
      .vpcId("vpc-1")
      .build()) >> CreateSecurityGroupResponse.builder()
      .groupId("sg-123")
      .build()

    then:
    result.securityGroup == SecurityGroup.builder().ownerId("id-test").groupId("sg-123").groupName("wideOpen").vpcId("vpc-1").description("desc").build()
  }

  void "should look up security group"() {
    when:
    final result = securityGroupLookup.getSecurityGroupByName("test", "wideOpen", "vpc-1").get()

    then:
    1 * amazonEC2.describeSecurityGroups(_) >> DescribeSecurityGroupsResponse.builder()
      .securityGroups([
             SecurityGroup.builder().ownerId("id-test").groupId("sg-123").groupName("wideOpen").vpcId("vpc-1").build()
      ])
      .build()

    then:
    result.securityGroup == SecurityGroup.builder().ownerId("id-test").groupId("sg-123").groupName("wideOpen").vpcId("vpc-1").build()

  }

  void "should look up security group, but not call AWS again"() {
    when:
    def result = securityGroupLookup.getSecurityGroupByName("test", "wideOpen", "vpc-1").get()

    then:
    1 * amazonEC2.describeSecurityGroups(_) >> DescribeSecurityGroupsResponse.builder()
      .securityGroups([
        SecurityGroup.builder().ownerId("id-test").groupId("sg-123").groupName("wideOpen").vpcId("vpc-1").build()
      ])
      .build()
    result.securityGroup == SecurityGroup.builder().ownerId("id-test").groupId("sg-123").groupName("wideOpen").vpcId("vpc-1").build()

    when:
    result = securityGroupLookup.getSecurityGroupByName("test", "wideOpen", "vpc-1").get()

    then:
    result.securityGroup == SecurityGroup.builder().ownerId("id-test").groupId("sg-123").groupName("wideOpen").vpcId("vpc-1").build()
    0 * _
  }

  void "should return empty on look up when security group does not exist"() {
    when:
    final result = securityGroupLookup.getSecurityGroupByName("test", "wideOpen", "vpc-1")

    then:
    1 * amazonEC2.describeSecurityGroups(_) >> DescribeSecurityGroupsResponse.builder()
      .securityGroups([
        SecurityGroup.builder().groupId("sg-456").groupName("NotTheGroupYouWereLokkingFor").vpcId("vpc-1").build()
      ])
      .build()

    then:
    !result.isPresent()
    0 * _
  }

  void "should add and remove ingress"() {
    final securityGroupUpdater = new SecurityGroupUpdater(
      SecurityGroup.builder().groupId("sg-123").build(), amazonEC2
    )

    when:
    securityGroupUpdater.addIngress([IpPermission.builder().fromPort(999).build()])

    then:
    1 * amazonEC2.authorizeSecurityGroupIngress(AuthorizeSecurityGroupIngressRequest.builder()
      .groupId("sg-123")
      .ipPermissions([IpPermission.builder().fromPort(999).build()])
      .build())
    0 * _

    when:
    securityGroupUpdater.removeIngress([IpPermission.builder().fromPort(111).build()])

    then:
    1 * amazonEC2.revokeSecurityGroupIngress(RevokeSecurityGroupIngressRequest.builder()
      .groupId("sg-123")
      .ipPermissions([IpPermission.builder().fromPort(111).build()])
      .build())

  }

}
