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

import com.netflix.spinnaker.oort.data.aws.Keys
import com.netflix.spinnaker.oort.security.aws.AmazonNamedAccount
import groovy.transform.Canonical
import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.client.RestTemplate
import reactor.event.Event

import static com.netflix.spinnaker.oort.ext.MapExtensions.specialSubtract
import static reactor.event.selector.Selectors.object

@CompileStatic
class AtlasHealthCachingAgent extends AbstractInfrastructureCachingAgent {
  static final String PROVIDER_NAME = "ATLAS"

  @Autowired
  RestTemplate restTemplate

  AtlasHealthCachingAgent(AmazonNamedAccount account, String region) {
    super(account, region)
  }

  private Map<String, Boolean> lastKnownHealths = [:]

  @Override
  void load() {
    if (!account.atlasHealth) return

    reactor.on(object("newHealth"), this.&loadHealth)
    reactor.on(object("missingHealth"), this.&removeHealth)

    def healths = (List<Map>)getAtlasHealth(String.format(account.atlasHealth, region))
    Map<String, Map> allHealths = healths.collectEntries { Map input -> [(input.id): input]}
    Map<String, Boolean> healthsThisRun = (Map<String, Boolean>)allHealths.collectEntries { instanceId, input -> [(instanceId): ((Map)input).isHealthy]}
    Map<String, Boolean> newHealths = specialSubtract(healthsThisRun, lastKnownHealths)
    Set<String> missingHealths = lastKnownHealths.keySet() - healthsThisRun.keySet()

    if (newHealths) {
      log.info "$cachePrefix - Adding ${newHealths.size()} new or changed Atlas Healths."
      for (instanceId in newHealths.keySet()) {
        def health = allHealths[instanceId]
        reactor.notify("newHealth", Event.wrap(new HealthContext(instanceId, health, region, account.name)))
      }
    }
    if (missingHealths) {
      log.info "$cachePrefix - Removing ${missingHealths.size()} removed Atlas Healths."
      for (instanceId in missingHealths) {
        reactor.notify("missingHealth", Event.wrap(new HealthContext(instanceId, null, region, account.name)))
      }
    }
    if (!newHealths && !missingHealths) {
      log.info "$cachePrefix - Nothing new to process"
    }

    lastKnownHealths = healthsThisRun
  }

  void loadHealth(Event<HealthContext> event) {
    def data = event.data
    cacheService.put(Keys.getInstanceHealthKey(data.instanceId, data.account, data.region, PROVIDER_NAME), data.health)
  }

  void removeHealth(Event<HealthContext> event) {
    def data = event.data
    cacheService.free(Keys.getInstanceHealthKey(data.instanceId, data.account, data.region, PROVIDER_NAME))
  }

  @Canonical
  static class HealthContext {
    String instanceId
    Map health
    String region
    String account
  }

  List getAtlasHealth(String baseUrl) {
    try {
      return (List)restTemplate.getForObject("${baseUrl}/api/v1/instance", List)
    } catch (IGNORE) {
    }
    return null
  }

  private String getCachePrefix() {
    "[caching:$region:${account.name}:atl]"
  }
}
