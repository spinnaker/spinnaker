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

  Set<TitusSecurityGroup> lookupSecurityGroupNames(String account, String region, LinkedHashSet<String> securityGroups) {
    Set<TitusSecurityGroup> expandedGroups = new LinkedHashSet<TitusSecurityGroup>()
    securityGroups.each { securityGroupId ->
      try {
        TitusSecurityGroup sg = new TitusSecurityGroup(groupId: securityGroupId)
        Map details = getSecurityGroupDetails(account, region, securityGroupId)
        if (details) {
          sg.groupName = details.name
          sg.awsAccount = details.awsAccount
          sg.awsVpcId = details.vpcId
        }
        expandedGroups << sg
      } catch (Exception e) {
      }
    }
    expandedGroups
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
     vpcId: awsDetails.vpcId
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
