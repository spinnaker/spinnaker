/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.google.provider.view

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter
import com.netflix.spinnaker.clouddriver.google.GoogleCloudProvider
import com.netflix.spinnaker.clouddriver.google.cache.Keys
import com.netflix.spinnaker.clouddriver.google.model.GoogleInstance
import com.netflix.spinnaker.clouddriver.google.model.GoogleSecurityGroup
import com.netflix.spinnaker.clouddriver.google.model.health.GoogleLoadBalancerHealth
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleLoadBalancer
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.model.InstanceProvider
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import static com.netflix.spinnaker.clouddriver.google.cache.Keys.Namespace.*

@Component
@Slf4j
class GoogleInstanceProvider implements InstanceProvider<GoogleInstance.View> {

  @Autowired
  final Cache cacheView

  @Autowired
  AccountCredentialsProvider accountCredentialsProvider

  @Autowired
  ObjectMapper objectMapper

  @Autowired
  GoogleSecurityGroupProvider securityGroupProvider

  final String platform = GoogleCloudProvider.GCE

  @Override
  GoogleInstance.View getInstance(String account, String region, String id) {
    Set<GoogleSecurityGroup> securityGroups = securityGroupProvider.getAll(false)
    def key = Keys.getInstanceKey(account, region, id)
    getInstanceCacheDatas([key])?.findResult { CacheData cacheData ->
      instanceFromCacheData(cacheData, account, securityGroups)?.view
    }
  }

  /**
   * Non-interface method for efficient building of GoogleInstance models during cluster or server group requests.
   */
  List<GoogleInstance> getInstances(String account, List<String> instanceKeys, Set<GoogleSecurityGroup> securityGroups) {
    getInstanceCacheDatas(instanceKeys)?.collect {
      instanceFromCacheData(it, account, securityGroups)
    }
  }

  Collection<CacheData> getInstanceCacheDatas(List<String> keys) {
    cacheView.getAll(INSTANCES.ns,
                     keys,
                     RelationshipCacheFilter.include(LOAD_BALANCERS.ns,
                                                     SERVER_GROUPS.ns))
  }

  @Override
  String getConsoleOutput(String account, String region, String id) {
    def accountCredentials = accountCredentialsProvider.getCredentials(account)

    if (!(accountCredentials instanceof GoogleNamedAccountCredentials)) {
      throw new IllegalArgumentException("Invalid credentials: ${account}:${region}")
    }

    def project = accountCredentials.project
    def compute = accountCredentials.compute
    def googleInstance = getInstance(account, region, id)

    if (googleInstance) {
      return compute.instances().getSerialPortOutput(project, googleInstance.zone, id).execute().contents
    }

    return null
  }

  GoogleInstance instanceFromCacheData(CacheData cacheData, String account, Set<GoogleSecurityGroup> securityGroups) {
    GoogleInstance instance = objectMapper.convertValue(cacheData.attributes, GoogleInstance)

    def loadBalancerKeys = cacheData.relationships[LOAD_BALANCERS.ns]
    if (loadBalancerKeys) {
      cacheView.getAll(LOAD_BALANCERS.ns, loadBalancerKeys).each { CacheData loadBalancerCacheData ->
        GoogleLoadBalancer loadBalancer = objectMapper.convertValue(loadBalancerCacheData.attributes, GoogleLoadBalancer)
        def foundHealths = loadBalancer.healths.findAll { GoogleLoadBalancerHealth health ->
          health.instanceName == instance.name
        }
        if (foundHealths) {
          // TODO(ttomsu): Instances attached to a load balancer without a health check will be marked as HEALTHY,
          // but this ignores the platform health state (RUNNING, STARTING, STOPPING, etc). According to the docs,
          // (https://cloud.google.com/compute/docs/load-balancing/health-checks#overview):
          //   "Health checks ensure that Compute Engine forwards new connections only to instances that are up and ready
          //   to receive them."
          // This implies that the load balancer state should be UNHEALTHY in cases where the platform health state is
          // not RUNNING.
          instance.loadBalancerHealths.addAll(foundHealths)
        }
      }
    }

    def serverGroupKey = cacheData.relationships[SERVER_GROUPS.ns]?.first()
    if (serverGroupKey) {
      instance.serverGroup = Keys.parse(serverGroupKey).serverGroup
    }

    instance.securityGroups = GoogleSecurityGroupProvider.getMatchingServerGroupNames(
        account,
        securityGroups,
        instance.tags.items as Set<String>,
        instance.networkName)

    instance
  }
}
