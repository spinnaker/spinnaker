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

package com.netflix.spinnaker.clouddriver.aws.model

import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.model.AuthorizeSecurityGroupIngressRequest
import com.amazonaws.services.ec2.model.CreateSecurityGroupRequest
import com.amazonaws.services.ec2.model.DescribeSecurityGroupsRequest
import com.amazonaws.services.ec2.model.Filter
import com.amazonaws.services.ec2.model.IpPermission
import com.amazonaws.services.ec2.model.RevokeSecurityGroupIngressRequest
import com.amazonaws.services.ec2.model.SecurityGroup
import com.google.common.collect.ImmutableSet
import com.netflix.spinnaker.clouddriver.aws.deploy.description.UpsertSecurityGroupDescription
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository

class SecurityGroupLookupFactory {

  private final AmazonClientProvider amazonClientProvider
  private final AccountCredentialsRepository accountCredentialsRepository

  SecurityGroupLookupFactory(AmazonClientProvider amazonClientProvider,
                           AccountCredentialsRepository accountCredentialsRepository) {
    this.amazonClientProvider = amazonClientProvider
    this.accountCredentialsRepository = accountCredentialsRepository
  }

  SecurityGroupLookup getInstance(String region) {
    final allNetflixAmazonCredentials = (Set<NetflixAmazonCredentials>) accountCredentialsRepository.all.findAll {
      it instanceof NetflixAmazonCredentials
    }
    final accounts = ImmutableSet.copyOf(allNetflixAmazonCredentials)
    new SecurityGroupLookup(amazonClientProvider, region, accounts)
  }

  /**
   * Allows look up for account names and security groups from a cache that lives as long as the instance.
   * Can also be used to create a security group from a description.
   */
  static class SecurityGroupLookup {
    private final AmazonClientProvider amazonClientProvider
    private final String region
    private final ImmutableSet<NetflixAmazonCredentials> accounts

    private final Map<String, SecurityGroup> securityGroupByName = [:]

    SecurityGroupLookup(AmazonClientProvider amazonClientProvider, String region,
                        ImmutableSet<NetflixAmazonCredentials> accounts) {
      this.amazonClientProvider = amazonClientProvider
      this.region = region
      this.accounts = accounts
    }

    private NetflixAmazonCredentials getCredentialsForName(String accountName) {
      accounts.find { it.name == accountName }
    }

    String getAccountIdForName(String accountName) {
      getCredentialsForName(accountName)?.accountId ?: accountName
    }

    SecurityGroupUpdater createSecurityGroup(UpsertSecurityGroupDescription description) {
      final credentials = getCredentialsForName(description.credentialAccount)
      final request = new CreateSecurityGroupRequest(description.name, description.description)
      if (description.vpcId) {
        request.withVpcId(description.vpcId)
      }
      final amazonEC2 = amazonClientProvider.getAmazonEC2(credentials, region, true)
      final result = amazonEC2.createSecurityGroup(request)
      final newSecurityGroup = new SecurityGroup(ownerId: credentials.accountId, groupId: result.groupId,
        groupName: description.name, description: description.description, vpcId: description.vpcId)
      new SecurityGroupUpdater(newSecurityGroup, amazonEC2)
    }

    SecurityGroupUpdater getSecurityGroupByName(String accountName, String name, String vpcId) {
      final credentials = getCredentialsForName(accountName)
      if (!credentials) { return null }

      def amazonEC2 = amazonClientProvider.getAmazonEC2(credentials, region, true)
      def cachedSecurityGroupKey = name.toLowerCase() + "." + vpcId
      def cachedSecurityGroup = securityGroupByName.get(cachedSecurityGroupKey)
      if (cachedSecurityGroup) {
        return new SecurityGroupUpdater(cachedSecurityGroup, amazonEC2)
      }

      def describeSecurityGroupsRequest = new DescribeSecurityGroupsRequest().withFilters(
        new Filter("group-name", [name])
      )

      def securityGroups = amazonEC2.describeSecurityGroups(describeSecurityGroupsRequest).securityGroups
      def securityGroup = securityGroups.find {
        it.groupName == name && it.vpcId == vpcId
      }
      if (securityGroup) {
        securityGroupByName[cachedSecurityGroupKey] = securityGroup
        return new SecurityGroupUpdater(securityGroup, amazonEC2)
      }
      null
    }
  }

  static class SecurityGroupUpdater {
    final SecurityGroup securityGroup
    private final AmazonEC2 amazonEC2

    SecurityGroup getSecurityGroup() {
      securityGroup
    }

    SecurityGroupUpdater(SecurityGroup securityGroup, AmazonEC2 amazonEC2) {
      this.securityGroup = securityGroup
      this.amazonEC2 = amazonEC2
    }

    void addIngress(List<IpPermission> ipPermissionsToAdd) {
      amazonEC2.authorizeSecurityGroupIngress(new AuthorizeSecurityGroupIngressRequest(
        groupId: securityGroup.groupId,
        ipPermissions: ipPermissionsToAdd
      ))
    }

    void removeIngress(List<IpPermission> ipPermissionsToRemove) {
      amazonEC2.revokeSecurityGroupIngress(new RevokeSecurityGroupIngressRequest(
        groupId: securityGroup.groupId,
        ipPermissions: ipPermissionsToRemove
      ))
    }

  }

}
