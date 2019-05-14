/*
 * Copyright 2017 Lookout, Inc.
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

package com.netflix.spinnaker.clouddriver.ecs.provider.agent;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.services.ecs.AmazonECS;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.cats.agent.CacheResult;
import com.netflix.spinnaker.cats.provider.ProviderCache;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import com.netflix.spinnaker.clouddriver.cache.OnDemandAgent;
import com.netflix.spinnaker.clouddriver.cache.OnDemandMetricsSupport;
import com.netflix.spinnaker.clouddriver.ecs.EcsCloudProvider;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

abstract class AbstractEcsOnDemandAgent<T> extends AbstractEcsCachingAgent<T>
    implements OnDemandAgent {
  final OnDemandMetricsSupport metricsSupport;

  AbstractEcsOnDemandAgent(
      NetflixAmazonCredentials account,
      String region,
      AmazonClientProvider amazonClientProvider,
      AWSCredentialsProvider awsCredentialsProvider,
      Registry registry) {
    super(account, region, amazonClientProvider, awsCredentialsProvider);
    this.metricsSupport =
        new OnDemandMetricsSupport(
            registry,
            this,
            EcsCloudProvider.ID
                + ":"
                + EcsCloudProvider.ID
                + ":${OnDemandAgent.OnDemandType.ServerGroup}");
  }

  @Override
  public OnDemandMetricsSupport getMetricsSupport() {
    return metricsSupport;
  }

  @Override
  public Collection<Map> pendingOnDemandRequests(ProviderCache providerCache) {
    return new LinkedList<>();
  }

  @Override
  public String getOnDemandAgentType() {
    return getAgentType();
  }

  @Override
  public boolean handles(OnDemandType type, String cloudProvider) {
    return type.equals(OnDemandType.ServerGroup) && cloudProvider.equals(EcsCloudProvider.ID);
  }

  @Override
  public OnDemandResult handle(ProviderCache providerCache, Map<String, ?> data) {
    if (!data.get("account").equals(accountName) || !data.get("region").equals(region)) {
      return null;
    }

    AmazonECS ecs = amazonClientProvider.getAmazonEcs(account, region, false);

    List<T> items = metricsSupport.readData(() -> getItems(ecs, providerCache));

    storeOnDemand(providerCache, data);

    CacheResult cacheResult =
        metricsSupport.transformData(
            () -> buildCacheResult(getAuthoritativeKeyName(), items, providerCache));

    return new OnDemandResult(
        getAgentType(),
        cacheResult,
        null); // TODO(Bruno Carrier) - evictions should happen properly instead of having a null
    // here
  }

  void storeOnDemand(ProviderCache providerCache, Map<String, ?> data) {
    // TODO: Overwrite if needed.
  }
}
