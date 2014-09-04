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
import com.netflix.spinnaker.oort.config.discovery.DiscoveryApi
import com.netflix.spinnaker.oort.model.discovery.DiscoveryApplication
import com.netflix.spinnaker.oort.model.discovery.DiscoveryApplications
import com.netflix.spinnaker.oort.model.discovery.DiscoveryInstance
import groovy.transform.CompileStatic
import retrofit.RetrofitError

@CompileStatic
class DiscoveryCachingAgent extends AbstractInfrastructureCachingAgent {

  public static final String PROVIDER_NAME = "discovery"

  private List<NetflixAmazonCredentials> accounts = []

  private Set<String> instanceIdsLastRun = new HashSet<>()

  DiscoveryApi discoveryApi

  DiscoveryCachingAgent(List<NetflixAmazonCredentials> accounts, String region, DiscoveryApi discoveryApi) {
    super(accounts.head(), region)
    this.accounts = accounts
    this.discoveryApi = discoveryApi
  }

  @Override
  void load() {
    String accountNames = accounts.name.join('|')
    long startTime = System.currentTimeMillis()
    log.info "$cachePrefix - loading discovery data from region $region for accounts ${accountNames}"

    DiscoveryApplications disco
    try {
      disco = discoveryApi.loadDiscoveryApplications()
    } catch (RetrofitError re) {
      log.warn "$cachePrefix - failed to query discovery: $re.message"
      return
    }

    log.info "$cachePrefix - discovery load and parse completed in ${System.currentTimeMillis() - startTime} milliseconds (${disco?.applications?.size()} applications)"
    log.info "$cachePrefix - instances ${disco.applications.collect { it.instances.size() }.sum()}"
    Set<String> instanceIdsThisRun = new HashSet<>()
    long cacheStart = System.currentTimeMillis()
    for (DiscoveryApplication application : disco.applications) {
      for (DiscoveryInstance instance : application.instances) {
        if (instance.instanceId) {
          instanceIdsThisRun.add(instance.instanceId)
          for (NetflixAmazonCredentials account : accounts) {
            cacheService.put(Keys.getInstanceHealthKey(instance.instanceId, account.name, region, PROVIDER_NAME), instance)
          }
        }
      }
    }
    log.info "$cachePrefix - instance health caching completed in ${System.currentTimeMillis() - cacheStart} milliseconds"
    long cacheClean = System.currentTimeMillis()
    instanceIdsLastRun.removeAll(instanceIdsThisRun)
    for (String instanceId : instanceIdsLastRun) {
      for (NetflixAmazonCredentials account : accounts) {
        cacheService.free(Keys.getInstanceHealthKey(instanceId, account.name, region, PROVIDER_NAME))
      }
    }
    log.info "$cachePrefix - removed ${instanceIdsLastRun.size()} missing instances in ${System.currentTimeMillis() - cacheClean} milliseconds"
    instanceIdsLastRun = instanceIdsThisRun
  }

  private String getCachePrefix() {
    String accountNames = accounts.name.join('|')
    "[caching:$region:$accountNames:dsc]"
  }
}
