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
import com.netflix.spinnaker.oort.config.atlas.AtlasHealthApi
import com.netflix.spinnaker.oort.model.atlas.AtlasInstanceHealth
import com.netflix.spinnaker.oort.security.aws.OortNetflixAmazonCredentials
import groovy.transform.CompileStatic

@CompileStatic
class AtlasHealthCachingAgent extends AbstractInfrastructureCachingAgent {
  static final String PROVIDER_NAME = "ATLAS"

  AtlasHealthApi atlasHealthApi
  private Set<String> instanceIdsLastRun = new HashSet<>()

  AtlasHealthCachingAgent(OortNetflixAmazonCredentials account, String region, AtlasHealthApi atlasHealthApi) {
    super(account, region)
    this.atlasHealthApi = atlasHealthApi
  }

  @Override
  void load() {
    long startTime = System.currentTimeMillis()
    log.info "$cachePrefix - loading atlas health data from region $region for account ${account.name}"

    List<AtlasInstanceHealth> atlas = atlasHealthApi.loadInstanceHealth()
    log.info "$cachePrefix - atlas read and parse completed in ${System.currentTimeMillis() - startTime} milliseconds (${atlas?.size()} instances)"

    Set<String> instanceIdsThisRun = new HashSet<>()
    long cacheStart = System.currentTimeMillis()
    for (AtlasInstanceHealth instance : atlas) {
      if (instance.instanceId) {
        instanceIdsThisRun.add(instance.instanceId)
        cacheService.put(Keys.getInstanceHealthKey(instance.instanceId, account.name, region, PROVIDER_NAME), instance)
      }
    }
    log.info "$cachePrefix - instance health caching completed in ${System.currentTimeMillis() - cacheStart} milliseconds"
    long cacheClean = System.currentTimeMillis()
    instanceIdsLastRun.removeAll(instanceIdsThisRun)
    for (String instanceId : instanceIdsLastRun) {
      cacheService.free(Keys.getInstanceHealthKey(instanceId, account.name, region, PROVIDER_NAME))
    }
    log.info "$cachePrefix - removed ${instanceIdsLastRun.size()} missing instances in ${System.currentTimeMillis() - cacheClean} milliseconds"
    instanceIdsLastRun = instanceIdsThisRun
  }

  private String getCachePrefix() {
    "[caching:$region:${account.name}:atl]"
  }
}
