/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.oort.data.aws.cachers

import com.netflix.spinnaker.amos.aws.NetflixAmazonCredentials
import com.netflix.spinnaker.oort.data.aws.Keys
import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.client.RestTemplate

import static com.netflix.spinnaker.oort.ext.MapExtensions.specialSubtract

@CompileStatic
class AtlasHealthCachingAgent extends AbstractInfrastructureCachingAgent {
  static final String PROVIDER_NAME = "ATLAS"

  //TODO-CF until I can merge the fix_health branch:
  Map<String, String> atlasHealth = [
    test: 'http://atlas-healthcheck-main.%s.dyntest.netflix.net:7001',
    prod: 'http://atlas-healthcheck-main.%s.dynprod.netflix.net:7001',
    mcetest: 'http://atlas-healthcheck-mce.%s.dyntest.netflix.net:7001',
    mceprod: 'http://atlas-healthcheck-mce.%s.dynprod.netflix.net:7001' ]

  @Autowired
  RestTemplate restTemplate

  AtlasHealthCachingAgent(NetflixAmazonCredentials account, String region) {
    super(account, region)
  }

  private Map<String, Boolean> lastKnownHealths = [:]

  @Override
  void load() {
    if (!atlasHealth[account.name]) return

    def healths = (List<Map>)getAtlasHealth(String.format(atlasHealth[account.name], region))
    Map<String, Map> allHealths = healths.collectEntries { Map input -> [(input.id): input]}
    Map<String, Boolean> healthsThisRun = (Map<String, Boolean>)allHealths.collectEntries { instanceId, input -> [(instanceId): ((Map)input).isHealthy]}
    Map<String, Boolean> newHealths = specialSubtract(healthsThisRun, lastKnownHealths)
    Set<String> missingHealths = new HashSet<String>(lastKnownHealths.keySet())
    missingHealths.removeAll(healthsThisRun.keySet())

    if (newHealths) {
      log.info "$cachePrefix - Adding ${newHealths.size()} new or changed Atlas Healths."
      for (instanceId in newHealths.keySet()) {
        def health = allHealths[instanceId]
        loadHealth(instanceId, region, account.name, health)
      }
    }
    if (missingHealths) {
      log.info "$cachePrefix - Removing ${missingHealths.size()} removed Atlas Healths."
      for (instanceId in missingHealths) {
        removeHealth(instanceId, region, account.name)
      }
    }
    if (!newHealths && !missingHealths) {
      log.info "$cachePrefix - Nothing new to process"
    }

    lastKnownHealths = healthsThisRun
  }

  void loadHealth(String instanceId, String region, String account, Map health) {
    cacheService.put(Keys.getInstanceHealthKey(instanceId, account, region, PROVIDER_NAME), health)
  }

  void removeHealth(String instanceId, String region, String account) {
    cacheService.free(Keys.getInstanceHealthKey(instanceId, account, region, PROVIDER_NAME))
  }

  List getAtlasHealth(String baseUrl) {
    try {
      return (List)restTemplate.getForObject("${baseUrl}/api/v1/instance", List)
    } catch (IGNORE) {
    }
    []
  }

  private String getCachePrefix() {
    "[caching:$region:${account.name}:atl]"
  }
}
