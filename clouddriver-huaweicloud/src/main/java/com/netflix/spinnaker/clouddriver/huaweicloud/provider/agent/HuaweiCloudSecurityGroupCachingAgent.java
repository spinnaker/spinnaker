/*
 * Copyright 2019 Huawei Technologies Co.,Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.netflix.spinnaker.clouddriver.huaweicloud.provider.agent;

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE;
import static com.netflix.spinnaker.clouddriver.huaweicloud.HuaweiCloudProvider.ID;
import static com.netflix.spinnaker.clouddriver.huaweicloud.cache.Keys.Namespace.SECURITY_GROUPS;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.cats.agent.AgentDataType;
import com.netflix.spinnaker.cats.agent.CacheResult;
import com.netflix.spinnaker.cats.agent.DefaultCacheResult;
import com.netflix.spinnaker.cats.provider.ProviderCache;
import com.netflix.spinnaker.clouddriver.cache.OnDemandAgent;
import com.netflix.spinnaker.clouddriver.cache.OnDemandAgent.OnDemandType;
import com.netflix.spinnaker.clouddriver.cache.OnDemandMetricsSupport;
import com.netflix.spinnaker.clouddriver.huaweicloud.security.HuaweiCloudNamedAccountCredentials;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

public class HuaweiCloudSecurityGroupCachingAgent extends AbstractHuaweiCloudCachingAgent
    implements OnDemandAgent {

  private final OnDemandMetricsSupport onDemandMetricsSupport;

  public HuaweiCloudSecurityGroupCachingAgent(
      HuaweiCloudNamedAccountCredentials credentials,
      ObjectMapper objectMapper,
      Registry registry,
      String region) {

    super(credentials, objectMapper, region);

    this.onDemandMetricsSupport =
        new OnDemandMetricsSupport(registry, this, ID + ":" + OnDemandType.SecurityGroup);
  }

  @Override
  public OnDemandMetricsSupport getMetricsSupport() {
    return this.onDemandMetricsSupport;
  }

  @Override
  public Collection<AgentDataType> getProvidedDataTypes() {
    return Collections.unmodifiableCollection(
        new ArrayList<AgentDataType>() {
          {
            add(AUTHORITATIVE.forType(SECURITY_GROUPS.ns));
          }
        });
  }

  @Override
  public String getAgentType() {
    return getAccountName() + "/" + region + "/" + this.getClass().getSimpleName();
  }

  @Override
  public String getOnDemandAgentType() {
    return getAgentType() + "-OnDemand";
  }

  @Override
  public boolean handles(OnDemandType type, String cloudProvider) {
    return OnDemandType.SecurityGroup.equals(type) && ID.equals(cloudProvider);
  }

  @Override
  public Collection<Map> pendingOnDemandRequests(ProviderCache providerCache) {
    // TODO: waiting to be implemented
    return Collections.emptyList();
  }

  @Override
  public CacheResult loadData(ProviderCache providerCache) {
    // TODO: waiting to be implemented
    return new DefaultCacheResult(Collections.emptyMap());
  }

  @Override
  public OnDemandAgent.OnDemandResult handle(
      ProviderCache providerCache, Map<String, ? extends Object> data) {
    // TODO: waiting to be implemented
    return new OnDemandAgent.OnDemandResult(
        getOnDemandAgentType(),
        new DefaultCacheResult(Collections.emptyMap()),
        Collections.emptyMap());
  }
}
