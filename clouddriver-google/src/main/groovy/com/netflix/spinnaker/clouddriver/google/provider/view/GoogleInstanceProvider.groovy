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
import com.netflix.spinnaker.clouddriver.google.model.GoogleInstance2
import com.netflix.spinnaker.clouddriver.google.model.GoogleLoadBalancer2
import com.netflix.spinnaker.clouddriver.google.model.health.GoogleLoadBalancerHealth
import com.netflix.spinnaker.clouddriver.google.security.GoogleCredentials
import com.netflix.spinnaker.clouddriver.model.InstanceProvider
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

import static com.netflix.spinnaker.clouddriver.google.cache.Keys.Namespace.INSTANCES
import static com.netflix.spinnaker.clouddriver.google.cache.Keys.Namespace.LOAD_BALANCERS
import static com.netflix.spinnaker.clouddriver.google.cache.Keys.Namespace.SERVER_GROUPS

@ConditionalOnProperty(value = "google.providerImpl", havingValue = "new")
@Component
@Slf4j
class GoogleInstanceProvider implements InstanceProvider<GoogleInstance2.View> {

  @Autowired
  final Cache cacheView

  @Autowired
  GoogleCloudProvider googleCloudProvider

  @Autowired
  AccountCredentialsProvider accountCredentialsProvider

  @Autowired
  ObjectMapper objectMapper

  final String platform = "gce"

  @Override
  GoogleInstance2.View getInstance(String account, String _ /*region*/, String id) {
    def pattern = Keys.getInstanceKey(googleCloudProvider, account, id)
    def identifiers = cacheView.filterIdentifiers(INSTANCES.ns, pattern)
    Collection<CacheData> cacheData = cacheView.getAll(INSTANCES.ns,
                                                       identifiers,
                                                       RelationshipCacheFilter.include(LOAD_BALANCERS.ns,
                                                                                       SERVER_GROUPS.ns))

    if (!cacheData) {
      return null
    }
    instanceFromCacheData(cacheData.first())
  }

  @Override
  String getConsoleOutput(String account, String region, String id) {
    def accountCredentials = accountCredentialsProvider.getCredentials(account)

    if (!(accountCredentials?.credentials instanceof GoogleCredentials)) {
      throw new IllegalArgumentException("Invalid credentials: ${account}:${region}")
    }

    def credentials = accountCredentials.credentials
    def project = credentials.project
    def compute = credentials.compute
    def googleInstance = getInstance(account, region, id)

    if (googleInstance) {
      return compute.instances().getSerialPortOutput(project, googleInstance.zone, id).execute().contents
    }

    return null
  }

  GoogleInstance2.View instanceFromCacheData(CacheData cacheData) {
    GoogleInstance2 instance = objectMapper.convertValue(cacheData.attributes, GoogleInstance2)

    def serverGroupKey = cacheData.relationships[SERVER_GROUPS.ns].first()
    if (serverGroupKey) {
      instance.set("serverGroup", Keys.parse(googleCloudProvider, serverGroupKey).serverGroup)
    }

    def loadBalancerKeys = cacheData.relationships[LOAD_BALANCERS.ns]
    cacheView.getAll(LOAD_BALANCERS.ns, loadBalancerKeys).each { CacheData loadBalancerCacheData ->
      GoogleLoadBalancer2 loadBalancer = objectMapper.convertValue(loadBalancerCacheData.attributes, GoogleLoadBalancer2)
      instance.loadBalancerHealths << loadBalancer.healths.findAll { GoogleLoadBalancerHealth health ->
        health.instanceName == instance.name
      }
    }

    instance.view
  }
}
