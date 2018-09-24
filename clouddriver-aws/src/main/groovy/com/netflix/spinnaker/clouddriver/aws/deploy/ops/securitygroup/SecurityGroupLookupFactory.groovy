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

import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.model.AuthorizeSecurityGroupIngressRequest
import com.amazonaws.services.ec2.model.CreateSecurityGroupRequest
import com.amazonaws.services.ec2.model.CreateTagsRequest
import com.amazonaws.services.ec2.model.DescribeSecurityGroupsRequest
import com.amazonaws.services.ec2.model.Filter
import com.amazonaws.services.ec2.model.IpPermission
import com.amazonaws.services.ec2.model.RevokeSecurityGroupIngressRequest
import com.amazonaws.services.ec2.model.SecurityGroup
import com.amazonaws.services.ec2.model.Tag
import com.google.common.collect.ImmutableSet
import com.netflix.spinnaker.clouddriver.aws.deploy.description.UpsertSecurityGroupDescription
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository
import com.netflix.spinnaker.kork.core.RetrySupport
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class SecurityGroupLookupFactory {

  private final AmazonClientProvider amazonClientProvider
  private final AccountCredentialsRepository accountCredentialsRepository

  SecurityGroupLookupFactory(AmazonClientProvider amazonClientProvider,
                             AccountCredentialsRepository accountCredentialsRepository) {
    this.amazonClientProvider = amazonClientProvider
    this.accountCredentialsRepository = accountCredentialsRepository
  }

  SecurityGroupLookup getInstance(String region) {
    getInstance(region, true)
  }

  SecurityGroupLookup getInstance(String region, boolean skipEdda) {
    final allNetflixAmazonCredentials = (Set<NetflixAmazonCredentials>) accountCredentialsRepository.all.findAll {
      it instanceof NetflixAmazonCredentials
    }
    final accounts = ImmutableSet.copyOf(allNetflixAmazonCredentials)
    new SecurityGroupLookup(amazonClientProvider, region, accounts, skipEdda)
  }

  /**
   * Allows look up for account names and security groups from a cache that lives as long as the instance.
   * Can also be used to create a security group from a description.
   */
  static class SecurityGroupLookup {
    private final Logger log = LoggerFactory.getLogger(getClass());
    private final RetrySupport retrySupport = new RetrySupport()

    private final AmazonClientProvider amazonClientProvider
    private final String region
    private final ImmutableSet<NetflixAmazonCredentials> accounts
    private final boolean skipEdda
    Map<String, List<SecurityGroup>> eddaCachedSecurityGroups = [:]

    private final Map<String, SecurityGroup> securityGroupByName = [:]
    private final Map<String, SecurityGroup> securityGroupById = [:]

    SecurityGroupLookup(AmazonClientProvider amazonClientProvider, String region,
                        ImmutableSet<NetflixAmazonCredentials> accounts) {
      this.amazonClientProvider = amazonClientProvider
      this.region = region
      this.accounts = accounts
      this.skipEdda = true
    }

    SecurityGroupLookup(AmazonClientProvider amazonClientProvider, String region,
                        ImmutableSet<NetflixAmazonCredentials> accounts, boolean skipEdda) {
      this.amazonClientProvider = amazonClientProvider
      this.region = region
      this.accounts = accounts
      this.skipEdda = skipEdda
    }

    NetflixAmazonCredentials getCredentialsForName(String accountName) {
      accounts.find { it.name == accountName }
    }

    NetflixAmazonCredentials getCredentialsForId(String accountId) {
      accounts.find { it.accountId == accountId }
    }

    String getAccountIdForName(String accountName) {
      getCredentialsForName(accountName)?.accountId ?: accountName
    }

    String getAccountNameForId(String accountId) {
      accounts.find { it.accountId == accountId }?.name ?: accountId
    }

    boolean accountIdExists(String accountId) {
      accounts.any { it.accountId == accountId }
    }

    SecurityGroupUpdater createSecurityGroup(UpsertSecurityGroupDescription description) {
      final credentials = getCredentialsForName(description.credentialAccount)
      final request = new CreateSecurityGroupRequest(description.name, description.description)
      if (description.vpcId) {
        request.withVpcId(description.vpcId)
      }
      final amazonEC2 = amazonClientProvider.getAmazonEC2(credentials, region, true)
      final result = amazonEC2.createSecurityGroup(request)
      final newSecurityGroup = new SecurityGroup(
        ownerId: credentials.accountId,
        groupId: result.groupId,
        groupName: description.name,
        description: description.description,
        vpcId: description.vpcId
      )
      securityGroupById.put(result.groupId, newSecurityGroup)
      securityGroupByName.put(description.name, newSecurityGroup)

      try {
        /*
         * `createSecurityGroup` is eventually consistent hence the need for retries in the event that the newly
         * created security group is not immediately taggable.
         */
        retrySupport.retry({
          CreateTagsRequest createTagRequest = new CreateTagsRequest()
          createTagRequest.withResources(result.groupId).withTags([
            new Tag("Name", description.name)
          ])
          amazonEC2.createTags(createTagRequest)
        }, 10, 3000, false);
      } catch (Exception e) {
        log.error(
          "Unable to tag newly created security group (groupName: {}, groupId: {}, accountId: {})",
          description.name,
          result.groupId,
          credentials.accountId,
          e
        )
      }

      if (!skipEdda) {
        getEddaSecurityGroups(amazonEC2, description.credentialAccount, region).add(newSecurityGroup)
      }
      new SecurityGroupUpdater(newSecurityGroup, amazonEC2)
    }

    Optional<SecurityGroupUpdater> getSecurityGroupByName(String accountName, String name, String vpcId) {
      final credentials = getCredentialsForName(accountName)
      if (!credentials) { return Optional.empty() }

      def amazonEC2 = amazonClientProvider.getAmazonEC2(credentials, region, skipEdda)
      def cachedSecurityGroupKey = name.toLowerCase() + "." + vpcId
      def cachedSecurityGroup = securityGroupByName.get(cachedSecurityGroupKey)
      if (cachedSecurityGroup) {
        return Optional.of(new SecurityGroupUpdater(cachedSecurityGroup, amazonEC2))
      }
      def securityGroups
      if (skipEdda) {
        def describeSecurityGroupsRequest = new DescribeSecurityGroupsRequest().withFilters(
          new Filter("group-name", [name])
        )
        securityGroups = amazonEC2.describeSecurityGroups(describeSecurityGroupsRequest).securityGroups
      } else {
        securityGroups = getEddaSecurityGroups(amazonEC2, accountName, region)
      }

      def securityGroup = securityGroups.find {
        it.groupName == name && it.vpcId == vpcId
      }
      if (securityGroup) {
        securityGroupByName[cachedSecurityGroupKey] = securityGroup
        securityGroupById[securityGroup.groupId + "." + vpcId] = securityGroup
        return Optional.of(new SecurityGroupUpdater(securityGroup, amazonEC2))
      }
      Optional.empty()
    }

    private List<SecurityGroup> getEddaSecurityGroups(AmazonEC2 amazonEC2, String accountName, String region) {
      def cacheKey = accountName + ':' + region
      if (!eddaCachedSecurityGroups.containsKey(cacheKey)) {
        eddaCachedSecurityGroups[cacheKey] = amazonEC2.describeSecurityGroups().securityGroups
      }
      return eddaCachedSecurityGroups[cacheKey]
    }

    Optional<SecurityGroupUpdater> getSecurityGroupById(String accountName, String groupId, String vpcId) {
      final credentials = getCredentialsForName(accountName)
      if (!credentials) { return Optional.empty() }

      def amazonEC2 = amazonClientProvider.getAmazonEC2(credentials, region, skipEdda)
      def cachedSecurityGroupKey = groupId.toLowerCase() + "." + vpcId
      def cachedSecurityGroup = securityGroupById.get(cachedSecurityGroupKey)
      if (cachedSecurityGroup) {
        return Optional.of(new SecurityGroupUpdater(cachedSecurityGroup, amazonEC2))
      }
      def securityGroups = []
      if (skipEdda) {
        def describeSecurityGroupsRequest = new DescribeSecurityGroupsRequest().withGroupIds(groupId)
        try {
          securityGroups = amazonEC2.describeSecurityGroups(describeSecurityGroupsRequest).securityGroups
        } catch (Exception ignored) {}
      } else {
        securityGroups = getEddaSecurityGroups(amazonEC2, accountName, region)
      }

      def securityGroup = securityGroups.find {
        it.groupId == groupId && it.vpcId == vpcId
      }
      if (securityGroup) {
        securityGroupById[cachedSecurityGroupKey] = securityGroup
        securityGroupByName[securityGroup.groupName.toLowerCase() + "." + vpcId] = securityGroup
        return Optional.of(new SecurityGroupUpdater(securityGroup, amazonEC2))
      }
      Optional.empty()
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
      securityGroup.ipPermissions.addAll(ipPermissionsToAdd)
    }

    void removeIngress(List<IpPermission> ipPermissionsToRemove) {
      amazonEC2.revokeSecurityGroupIngress(new RevokeSecurityGroupIngressRequest(
        groupId: securityGroup.groupId,
        ipPermissions: ipPermissionsToRemove
      ))
      securityGroup.ipPermissions.removeAll(ipPermissionsToRemove)
    }

  }

}
