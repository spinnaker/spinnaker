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

import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter
import com.netflix.spinnaker.clouddriver.aws.cache.Keys
import com.netflix.spinnaker.clouddriver.aws.model.AmazonVpc
import com.netflix.spinnaker.clouddriver.aws.provider.AwsProvider
import com.netflix.spinnaker.clouddriver.aws.provider.view.AmazonLoadBalancerProvider
import com.netflix.spinnaker.clouddriver.aws.provider.view.AmazonSecurityGroupProvider
import com.netflix.spinnaker.clouddriver.aws.provider.view.AmazonVpcProvider
import com.netflix.spinnaker.clouddriver.aws.security.AmazonCredentials
import com.netflix.spinnaker.clouddriver.aws.services.RegionScopedProviderFactory
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import com.netflix.spinnaker.clouddriver.titus.client.model.Job
import com.netflix.spinnaker.clouddriver.titus.credentials.NetflixTitusCredentials
import com.netflix.spinnaker.clouddriver.titus.model.TitusInstance
import com.netflix.spinnaker.clouddriver.titus.model.TitusSecurityGroup
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import javax.annotation.PostConstruct

import static com.netflix.spinnaker.clouddriver.aws.cache.Keys.Namespace.SECURITY_GROUPS
import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.HEALTH

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

  @Autowired
  AwsProvider awsProvider

  @Autowired
  AmazonLoadBalancerProvider amazonLoadBalancerProvider

  Set<TitusSecurityGroup> lookupSecurityGroupNames(Map<String, TitusSecurityGroup> titusSecurityGroupLookupCache,
                                                   String account,
                                                   String region,
                                                   List<String> securityGroups) {
    Set<TitusSecurityGroup> expandedGroups = new LinkedHashSet<TitusSecurityGroup>()
    Set<String> missingSecurityGroupIds = []

    securityGroups.each { securityGroupId ->
      def titusSecurityGroupLookupCacheId = "${account}-${region}-${securityGroupId}".toString()
      TitusSecurityGroup titusSecurityGroup = titusSecurityGroupLookupCache.get(titusSecurityGroupLookupCacheId)

      if (!titusSecurityGroup) {
        missingSecurityGroupIds << securityGroupId
      } else {
        expandedGroups << titusSecurityGroup
      }
    }

    if (missingSecurityGroupIds) {
      def securityGroupNamesByIdentifier = awsSecurityGroupProvider.cacheView.getIdentifiers(
        SECURITY_GROUPS.ns
      ).collectEntries {
        def key = Keys.parse(it)
        [key.id, key.name]
      }

      Map awsDetails = awsAccountLookup.find {
        it.titusAccount == account && it.region == region
      }

      expandedGroups.addAll(missingSecurityGroupIds.collect {
        def titusSecurityGroup = new TitusSecurityGroup(groupId: it)
        titusSecurityGroup.groupName = securityGroupNamesByIdentifier[it]
        titusSecurityGroup.awsAccount = awsDetails.awsAccount
        titusSecurityGroup.awsVpcId = awsDetails.vpcId

        def titusSecurityGroupLookupCacheId = "${account}-${region}-${it}".toString()
        titusSecurityGroupLookupCache.put(titusSecurityGroupLookupCacheId, titusSecurityGroup)

        return titusSecurityGroup
      })
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

  void lookupTargetGroupHealth(Job job, Set<TitusInstance> instances) {
    def loadBalancingHealthAgents = awsProvider.healthAgents.findAll {
      it.healthId.contains('load-balancer-v2-target-group')
    }
    Map<String, TitusInstance> keysToInstance = [:]
    instances.each { instance ->
      loadBalancingHealthAgents.each {
        keysToInstance[getTargetGroupHealthKey(job, instance, it.healthId)] = instance
      }
    }
    Collection<CacheData> healths = amazonLoadBalancerProvider.cacheView.getAll(HEALTH.ns, keysToInstance.keySet(), RelationshipCacheFilter.none())
    healths.findAll { it.attributes.type == 'TargetGroup' && it.attributes.targetGroups }.each { healthEntry ->
      keysToInstance.get(healthEntry.id).health.addAll(healthEntry.attributes)
    }
  }

  private String getTargetGroupHealthKey(Job job, TitusInstance instance, String healthKey) {
    String region = instance.placement.region
    String account = job.labels.spinnakerAccount
    String awsAccount = lookupAccount(account, region).awsAccount
    String containerIp = instance.placement.containerIp
    return com.netflix.spinnaker.clouddriver.aws.data.Keys.getInstanceHealthKey(containerIp, awsAccount, region, healthKey)
  }

  private Map lookupAccount(account, region) {
    Map awsDetails = awsAccountLookup.find {
      it.titusAccount == account && it.region == region
    }
    if (!awsDetails) {
      return null
    }
    awsDetails
  }

  String awsAccountId(account, region) {
    Map awsDetails = lookupAccount(account, region)
    if (!awsDetails) {
      return null
    }
    accountCredentialsProvider.all.find {
      it instanceof AmazonCredentials && it.name == awsDetails.awsAccount
    }?.accountId
  }

  String awsVpcId(account, region) {
    lookupAccount(account, region)?.vpcId
  }

  String stack(account) {
    accountCredentialsProvider.all.find {
      it instanceof NetflixTitusCredentials && it.name == account
    }?.stack
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

    [name: awsSecurityGroupProvider.getById(awsDetails.awsAccount,
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
