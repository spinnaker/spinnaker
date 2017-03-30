/*
 * Copyright 2017 Google, Inc.
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
import com.google.api.services.appengine.v1.model.Application
import com.netflix.spinnaker.cats.agent.AgentDataType
import com.netflix.spinnaker.cats.agent.CacheResult
import com.netflix.spinnaker.cats.agent.DefaultCacheResult
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.appengine.cache.Keys
import com.netflix.spinnaker.clouddriver.appengine.model.AppenginePlatformApplication
import com.netflix.spinnaker.clouddriver.appengine.security.AppengineNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.kubernetes.provider.view.MutableCacheData
import groovy.util.logging.Slf4j

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE

@Slf4j
class AppenginePlatformApplicationCachingAgent extends AbstractAppengineCachingAgent {
  String agentType = "${accountName}/${AppenginePlatformApplicationCachingAgent.simpleName}"

  static final Set<AgentDataType> types = Collections.unmodifiableSet([
    AUTHORITATIVE.forType(Keys.Namespace.PLATFORM_APPLICATIONS.ns)] as Set
  )

  AppenginePlatformApplicationCachingAgent(String accountName,
                                           AppengineNamedAccountCredentials credentials,
                                           ObjectMapper objectMapper) {
    super(accountName, objectMapper, credentials)
  }

  @Override
  String getSimpleName() {
    AppenginePlatformApplicationCachingAgent.simpleName
  }

  @Override
  Collection<AgentDataType> getProvidedDataTypes() {
    types
  }

  @Override
  CacheResult loadData(ProviderCache providerCache) {
    def platformApplicationName = credentials.project
    Application platformApplication = credentials.appengine.apps().get(platformApplicationName).execute()
    Map<String, MutableCacheData> cachedPlatformApplications = MutableCacheData.mutableCacheMap()
    def apiApplicationKey = Keys.getPlatformApplicationKey(platformApplicationName)

    cachedPlatformApplications[apiApplicationKey].with {
      attributes.name = platformApplicationName
      attributes.platformApplication = new AppenginePlatformApplication(platformApplication)
    }

    log.info("Caching ${cachedPlatformApplications.size()} platform applications in ${agentType}")
    return new DefaultCacheResult([(Keys.Namespace.PLATFORM_APPLICATIONS.ns): cachedPlatformApplications.values()], [:])
  }
}
