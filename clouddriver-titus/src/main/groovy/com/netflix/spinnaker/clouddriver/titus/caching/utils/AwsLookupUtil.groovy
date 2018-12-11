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
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import javax.annotation.PostConstruct

import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.HEALTH

@Component
@Slf4j
class AwsLookupUtil {

  private Set<AmazonVpc> amazonVpcs
  private List awsAccountLookup


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

  Boolean securityGroupIdExists(String account, String region, String securityGroupId) {
    getSecurityGroupDetails(account, region, securityGroupId)?.name != null
  }

  String convertSecurityGroupNameToId(account, region, providedSecurityGroup) {
    Map awsDetails = awsAccountLookup.find {
      it.titusAccount == account && it.region == region
    }
    awsSecurityGroupProvider.getIdByName(awsDetails.awsAccount, region, providedSecurityGroup, awsDetails.vpcId)
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
    String awsAccount = lookupAccount(account, region)?.awsAccount
    String containerIp = instance.placement.containerIp
    return com.netflix.spinnaker.clouddriver.aws.data.Keys.getInstanceHealthKey(containerIp, awsAccount, region, healthKey)
  }

  public Map lookupAccount(account, region) {
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

  String awsAccountName(account, region) {
    Map awsDetails = lookupAccount(account, region)
    if (!awsDetails) {
      return null
    }
    return awsDetails.awsAccount
  }

  String awsVpcId(account, region) {
    String vpcId = lookupAccount(account, region)?.vpcId
    if (!vpcId) {
      refreshAwsAccounts()
      vpcId = lookupAccount(account, region)?.vpcId
      log.error("got empty vpcId for ${account} ${region}, reloaded value is ${vpcId}")
    }
    return vpcId
  }

  String stack(account) {
    accountCredentialsProvider.all.find {
      it instanceof NetflixTitusCredentials && it.name == account
    }?.stack
  }

  private String convertVpcNameToId(String awsAccount, String region, String name) {
    if (!amazonVpcs) {
      amazonVpcs = amazonVpcProvider.getAll()
    }
    amazonVpcs.find { it.name == name && it.account == awsAccount && it.region == region }?.id
  }

  private Map getSecurityGroupDetails(String account, String region, String securityGroupId) {
    Map awsDetails = awsAccountLookup.find {
      it.titusAccount == account && it.region == region
    }

    if (!awsDetails) {
      return null
    }

    if (!awsDetails.vpcId) { // locally we sometimes fail to resolve the vpcId on init. Try again before failing here.
      refreshAwsAccounts()
      awsDetails = awsAccountLookup.find {
        it.titusAccount == account && it.region == region
      }
      if (!awsDetails.vpcId) {
        return null
      }
    }

    [name      : awsSecurityGroupProvider.getNameById(
      awsDetails.awsAccount,
      region,
      securityGroupId,
      awsDetails.vpcId
     ),
     awsAccount: awsDetails.awsAccount,
     vpcId     : awsDetails.vpcId
    ]

  }

  private void refreshAwsAccounts() {
    amazonVpcs = amazonVpcProvider.getAll()
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

  @PostConstruct
  private void init() {
    if (!awsAccountLookup) {
      refreshAwsAccounts()
    }
  }

}
