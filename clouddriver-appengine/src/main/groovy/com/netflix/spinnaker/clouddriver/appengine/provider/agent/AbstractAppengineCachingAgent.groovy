/*
 * Copyright 2016 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.appengine.provider.agent

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.api.client.googleapis.batch.BatchRequest
import com.netflix.spinnaker.cats.agent.AccountAware
import com.netflix.spinnaker.cats.agent.CachingAgent
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.clouddriver.appengine.AppengineCloudProvider
import com.netflix.spinnaker.clouddriver.appengine.provider.AppengineProvider
import com.netflix.spinnaker.clouddriver.appengine.security.AppengineNamedAccountCredentials

abstract class AbstractAppengineCachingAgent implements CachingAgent, AccountAware {
  final String accountName
  final String providerName = AppengineProvider.PROVIDER_NAME
  final AppengineCloudProvider appengineCloudProvider = new AppengineCloudProvider()
  final ObjectMapper objectMapper
  final AppengineNamedAccountCredentials credentials

  AbstractAppengineCachingAgent(String accountName,
                                ObjectMapper objectMapper,
                                AppengineNamedAccountCredentials credentials) {
    this.accountName = accountName
    this.objectMapper = objectMapper
    this.credentials = credentials
  }

  boolean shouldIgnoreLoadBalancer(String loadBalancerName) {
    if (credentials.services != null && !credentials.services.isEmpty() &&
      credentials.services.every { !loadBalancerName.matches(it) }) {
      return true
    }
    if (credentials.omitServices != null && !credentials.omitServices.isEmpty() &&
      credentials.omitServices.any { loadBalancerName.matches(it) }) {
      return true
    }
    return false
  }

  boolean shouldIgnoreServerGroup(String serverGroupName) {
    if (credentials.versions != null && !credentials.versions.isEmpty() &&
      credentials.versions.every { !serverGroupName.matches(it) }) {
      return true
    }
    if (credentials.omitVersions != null && !credentials.omitVersions.isEmpty()
      && credentials.omitVersions.any { serverGroupName.matches(it) }) {
      return true
    }
    return false
  }

  static void cache(Map<String, List<CacheData>> cacheResults,
                    String cacheNamespace,
                    Map<String, CacheData> cacheDataById) {
    cacheResults[cacheNamespace].each {
      def existingCacheData = cacheDataById[it.id]
      if (existingCacheData) {
        existingCacheData.attributes.putAll(it.attributes)
        it.relationships.each { String relationshipName, Collection<String> relationships ->
          existingCacheData.relationships[relationshipName].addAll(relationships)
        }
      } else {
        cacheDataById[it.id] = it
      }
    }
  }

  static void executeIfRequestsAreQueued(BatchRequest batch) {
    if (batch.size()) {
      batch.execute()
    }
  }

  abstract String getSimpleName()
}
