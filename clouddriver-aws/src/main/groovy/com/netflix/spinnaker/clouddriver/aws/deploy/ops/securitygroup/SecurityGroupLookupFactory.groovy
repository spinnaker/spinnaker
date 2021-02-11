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
import com.amazonaws.services.ec2.model.*
import com.google.common.collect.ImmutableSet
import com.netflix.spinnaker.clouddriver.aws.deploy.description.UpsertSecurityGroupDescription
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import com.netflix.spinnaker.credentials.CredentialsRepository
import com.netflix.spinnaker.kork.core.RetrySupport
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import com.netflix.spinnaker.kork.exceptions.IntegrationException
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class SecurityGroupLookupFactory {

  private final AmazonClientProvider amazonClientProvider
  private final CredentialsRepository<NetflixAmazonCredentials> credentialsRepository

  SecurityGroupLookupFactory(AmazonClientProvider amazonClientProvider,
                             CredentialsRepository<NetflixAmazonCredentials> credentialsRepository) {
    this.amazonClientProvider = amazonClientProvider
    this.credentialsRepository = credentialsRepository
  }

  SecurityGroupLookup getInstance(String region) {
    getInstance(region, true)
  }

  SecurityGroupLookup getInstance(String region, boolean skipEdda) {
    final allNetflixAmazonCredentials = credentialsRepository.getAll()
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
      final credentials = getCredentialsForName(description.account)
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
          Collection<Tag> tags = new HashSet()
          tags.add(new Tag("Name", description.name))
          description.tags.each {
            entry -> tags.add(new Tag(entry.key, entry.value))
          }
          createTagRequest.withResources(result.groupId).withTags(tags)

          try {
            amazonEC2.createTags(createTagRequest)
          } catch (Exception e) {
            log.warn("Unable to tag newly created security group '${description.name}', reason: ${e.getMessage()}")
            throw e
          }

          log.info("Succesfully tagged newly created security group '${description.name}'")

          try {
            def describeSecurityGroupsRequest = new DescribeSecurityGroupsRequest().withFilters(
              new Filter("group-name", [description.name])
            )
            def securityGroups = amazonEC2.describeSecurityGroups(describeSecurityGroupsRequest).securityGroups
            if (!securityGroups) {
              throw new IntegrationException("Not Found!").setRetryable(true)
            }
          } catch (Exception e) {
            log.warn("Unable to describe newly created security group '${description.name}', reason: ${e.getMessage()}")
            throw e
          }

          log.info("Succesfully described newly created security group '${description.name}'")
        }, 15, 3000, false);
      } catch (Exception e) {
        log.error(
          "Unable to tag or describe newly created security group (groupName: {}, groupId: {}, accountId: {})",
          description.name,
          result.groupId,
          credentials.accountId,
          e
        )
      }

      if (!skipEdda) {
        getEddaSecurityGroups(amazonEC2, description.account, region).add(newSecurityGroup)
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
    private final Logger log = LoggerFactory.getLogger(getClass())

    SecurityGroup getSecurityGroup() {
      securityGroup
    }

    SecurityGroupUpdater(SecurityGroup securityGroup, AmazonEC2 amazonEC2) {
      this.securityGroup = securityGroup
      this.amazonEC2 = amazonEC2
    }

    void updateIngress(List<IpPermission> ipPermissionsToUpdate) {
      amazonEC2.updateSecurityGroupRuleDescriptionsIngress(new UpdateSecurityGroupRuleDescriptionsIngressRequest(
        groupId: securityGroup.groupId,
        ipPermissions: ipPermissionsToUpdate
      ))
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

    void updateTags(UpsertSecurityGroupDescription description, DynamicConfigService dynamicConfigService) {
      String groupId = securityGroup.groupId
      try {

        //fetch -> delete -> create new tags to ensure they are consistent
        DescribeTagsRequest describeTagsRequest = new DescribeTagsRequest().withFilters(
          new Filter("resource-id", [groupId])
        )
        DescribeTagsResult tagsResult = amazonEC2.describeTags(describeTagsRequest)
        List<TagDescription> currentTags = tagsResult.getTags()
        Collection<Tag> oldTags = new HashSet()
        // Filter Spinnaker specific tags, update to other tags might result in permission errors
        def additionalTags = dynamicConfigService.getConfig(String.class, "aws.features.security-group.additional-tags","")
        currentTags.each {
          it ->
            if (it.key.equals("Name") || description.tags?.keySet()?.contains(it.key) || additionalTags.contains(it.key)) {
              oldTags.add(new Tag(it.key, it.value))
            }
        }

        DeleteTagsRequest deleteTagsRequest = new DeleteTagsRequest()
          .withResources(groupId)
          .withTags(oldTags)
        amazonEC2.deleteTags(deleteTagsRequest)

        CreateTagsRequest createTagRequest = new CreateTagsRequest()
        Collection<Tag> tags = new HashSet()
        tags.add(new Tag("Name", description.name))
        description.tags.each {
          entry -> tags.add(new Tag(entry.key, entry.value))
        }
        createTagRequest.withResources(groupId).withTags(tags)
        amazonEC2.createTags(createTagRequest)

      } catch (Exception e) {
        log.error(
          "Unable to update tags for security group (groupName: {}, groupId: {})",
          description.name,
          groupId,
          e
        )
      }
    }

  }

}
