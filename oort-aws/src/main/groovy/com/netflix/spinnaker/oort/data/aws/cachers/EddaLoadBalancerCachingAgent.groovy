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
import com.netflix.spinnaker.oort.config.edda.EddaApi
import com.netflix.spinnaker.oort.data.aws.Keys
import com.netflix.spinnaker.oort.model.edda.InstanceLoadBalancers
import com.netflix.spinnaker.oort.model.edda.LoadBalancerInstanceState
import retrofit.RetrofitError

class EddaLoadBalancerCachingAgent extends AbstractInfrastructureCachingAgent {

  public static final String PROVIDER_NAME = "edda-load-balancers"

  private Set<String> instanceIdsLastRun = new HashSet<>()

  EddaApi eddaApi

  EddaLoadBalancerCachingAgent(NetflixAmazonCredentials account, String region, EddaApi eddaApi) {
    super(account, region)
    this.eddaApi = eddaApi
  }

  @Override
  void load() {
    long startTime = System.currentTimeMillis()
    log.info "$cachePrefix - loading edda load balancer instance data from region $region for account ${account.name}"

    List<LoadBalancerInstanceState> loadBalancerInstances
    try {
      loadBalancerInstances = eddaApi.loadBalancerInstances()
    } catch (RetrofitError re) {
      log.warn "$cachePrefix - failed to query edda: $re.message"
      return
    }

    log.info "$cachePrefix - edda load retrieved ${loadBalancerInstances.size()} loadBalancers in ${System.currentTimeMillis() - startTime} milliseconds"
    long translateTime = System.currentTimeMillis()

    List<InstanceLoadBalancers> instances = InstanceLoadBalancers.fromLoadBalancerInstanceState(loadBalancerInstances)

    log.info "$cachePrefix - extracted ${instances.size()} instances in ${System.currentTimeMillis() - translateTime} milliseconds"

    Set<String> instanceIdsThisRun = new HashSet<>()
    long cacheStart = System.currentTimeMillis()
    for (InstanceLoadBalancers instance : instances) {
      instanceIdsThisRun.add(instance.instanceId)
      cacheService.put(Keys.getInstanceHealthKey(instance.instanceId, account.name, region, PROVIDER_NAME), instance)
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
    "[caching:$region:$account.name:lbh]"
  }

}
