package com.netflix.spinnaker.clouddriver.aws.model

import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.model.AuthorizeSecurityGroupIngressRequest
import com.amazonaws.services.ec2.model.CreateSecurityGroupRequest
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

  static class SecurityGroupLookup {
    private final AmazonClientProvider amazonClientProvider
    private final String region
    private final ImmutableSet<NetflixAmazonCredentials> accounts

    private final Map<String, List<SecurityGroup>> securityGroupsByAccount = [:]

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
      final amazonEC2 = amazonClientProvider.getAmazonEC2(credentials, region, true)
      final securityGroup = getSecurityGroups(accountName, amazonEC2).find {
        it.groupName == name && it.vpcId == vpcId
      }
      if (securityGroup) {
        return new SecurityGroupUpdater(securityGroup, amazonEC2)
      }
      null
    }

    private List<SecurityGroup> getSecurityGroups(String accountName, AmazonEC2 amazonEC2) {
      List<SecurityGroup> securityGroupsForAccount = securityGroupsByAccount[accountName]
      if (!securityGroupsForAccount) {
        securityGroupsForAccount = amazonEC2.describeSecurityGroups().securityGroups
        securityGroupsByAccount[accountName] = securityGroupsForAccount
      }
      securityGroupsForAccount
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
