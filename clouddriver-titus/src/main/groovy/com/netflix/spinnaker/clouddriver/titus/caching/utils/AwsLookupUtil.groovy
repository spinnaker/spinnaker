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

package com.netflix.spinnaker.clouddriver.titus.caching.utils

import com.netflix.spinnaker.clouddriver.aws.model.AmazonVpc
import com.netflix.spinnaker.clouddriver.aws.provider.view.AmazonSecurityGroupProvider
import com.netflix.spinnaker.clouddriver.aws.provider.view.AmazonVpcProvider
import com.netflix.spinnaker.clouddriver.aws.security.AmazonCredentials
import com.netflix.spinnaker.clouddriver.aws.services.RegionScopedProviderFactory
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import com.netflix.spinnaker.clouddriver.titus.credentials.NetflixTitusCredentials
import com.netflix.spinnaker.clouddriver.titus.model.TitusSecurityGroup
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import javax.annotation.PostConstruct

@Component
class AwsLookupUtil {

  private Set<AmazonVpc> amazonVpcs
  private List awsAccountLookup;

  @Autowired
  AmazonSecurityGroupProvider awsSecurityGroupProvider

  @Autowired
  AmazonVpcProvider amazonVpcProvider

  @Autowired
  AccountCredentialsProvider accountCredentialsProvider

  @Autowired
  RegionScopedProviderFactory regionScopedProviderFactory

  Set<TitusSecurityGroup> lookupSecurityGroupNames(Map<String, TitusSecurityGroup> titusSecurityGroupLookupCache,
                                                   String account,
                                                   String region,
                                                   LinkedHashSet<String> securityGroups) {
    Set<TitusSecurityGroup> expandedGroups = new LinkedHashSet<TitusSecurityGroup>()
    securityGroups.each { securityGroupId ->
      def titusSecurityGroupLookupCacheId = "${account}-${region}-${securityGroupId}".toString()
      TitusSecurityGroup titusSecurityGroup = titusSecurityGroupLookupCache.get(titusSecurityGroupLookupCacheId)

      try {
        if (!titusSecurityGroup) {
          titusSecurityGroup = new TitusSecurityGroup(groupId: securityGroupId)
          Map details = getSecurityGroupDetails(account, region, securityGroupId)
          if (details) {
            titusSecurityGroup.groupName = details.name
            titusSecurityGroup.awsAccount = details.awsAccount
            titusSecurityGroup.awsVpcId = details.vpcId
          }
          titusSecurityGroupLookupCache.put(titusSecurityGroupLookupCacheId, titusSecurityGroup)
        }
        expandedGroups << titusSecurityGroup
      } catch (Exception ignored) {
      }
    }
    expandedGroups
  }

  Boolean securityGroupIdExists(String account, String region, String securityGroupId) {
    getSecurityGroupDetails(account, region, securityGroupId)?.name != null
  }

  String convertSecurityGroupNameToId(account, region, providedSecurityGroup) {
    Map awsDetails = awsAccountLookup.find {
      it.titusAccount == account && it.region == region
    }
    awsSecurityGroupProvider.get(awsDetails.awsAccount, region, providedSecurityGroup, awsDetails.vpcId)?.id
  }

  String createSecurityGroupForApplication(account, region, application) {
    Map awsDetails = awsAccountLookup.find {
      it.titusAccount == account && it.region == region
    }
    String applicationSecurityGroup
    RegionScopedProviderFactory.RegionScopedProvider regionScopedProvider = regionScopedProviderFactory.forRegion(accountCredentialsProvider.all.find {
      it instanceof AmazonCredentials && it.name == awsDetails.awsAccount
    }, region)
    // check against the list of security groups to make sure we haven't just missed it in the cache
    def securityGroups = regionScopedProvider.securityGroupService.getSecurityGroupIds([application], awsDetails.vpcId, false)
    if (securityGroups[application]) {
      applicationSecurityGroup = securityGroups[application]
    }
    if (!applicationSecurityGroup) {
      applicationSecurityGroup = regionScopedProvider.securityGroupService.createSecurityGroupWithVpcId(application, awsDetails.vpcId)
    }
    applicationSecurityGroup
  }

  private String convertVpcNameToId(String awsAccount, String region, String name) {
    if (!amazonVpcs) {
      amazonVpcs = amazonVpcProvider.all
    }
    amazonVpcs.find { it.name == name && it.account == awsAccount && it.region == region }?.id
  }

  private Map getSecurityGroupDetails(String account, String region, String securityGroupId) {
    Map awsDetails = awsAccountLookup.find {
      it.titusAccount == account && it.region == region
    }

    if (!awsDetails || !awsDetails.vpcId) {
      return null
    }

    [name      : awsSecurityGroupProvider.getById(awsDetails.awsAccount,
      region,
      securityGroupId,
      awsDetails.vpcId
    )?.name,
     awsAccount: awsDetails.awsAccount,
     vpcId     : awsDetails.vpcId
    ]

  }

  @PostConstruct
  private void init() {
    if (!awsAccountLookup) {
      awsAccountLookup = []
      accountCredentialsProvider.all.findAll {
        it instanceof NetflixTitusCredentials
      }.each { NetflixTitusCredentials credential ->
        credential.regions.each { region ->
          awsAccountLookup << [titusAccount: credential.name,
                               awsAccount  : credential.awsAccount,
                               region      : region.name,
                               vpcId       : convertVpcNameToId(credential.awsAccount, region.name, credential.awsVpc)]
        }
      }
    }
  }

}
