/*
 * Copyright 2019 Huawei Technologies Co.,Ltd.
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

package com.netflix.spinnaker.clouddriver.huaweicloud.provider;

import static com.netflix.spinnaker.clouddriver.huaweicloud.cache.Keys.Namespace.INSTANCE_TYPES;
import static com.netflix.spinnaker.clouddriver.huaweicloud.cache.Keys.Namespace.NETWORKS;
import static com.netflix.spinnaker.clouddriver.huaweicloud.cache.Keys.Namespace.SECURITY_GROUPS;
import static com.netflix.spinnaker.clouddriver.huaweicloud.cache.Keys.Namespace.SUBNETS;

import com.netflix.spinnaker.cats.agent.Agent;
import com.netflix.spinnaker.cats.agent.AgentSchedulerAware;
import com.netflix.spinnaker.clouddriver.cache.SearchableProvider;
import com.netflix.spinnaker.clouddriver.cache.SearchableProvider.SearchableResource;
import com.netflix.spinnaker.clouddriver.huaweicloud.cache.Keys;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@ConditionalOnProperty("huaweicloud.enabled")
public class HuaweiCloudInfrastructureProvider extends AgentSchedulerAware
    implements SearchableProvider {

  private final Collection<Agent> agents;

  private final Set<String> defaultCaches =
      new HashSet<String>() {
        {
          add(INSTANCE_TYPES.ns);
          add(NETWORKS.ns);
          add(SECURITY_GROUPS.ns);
          add(SUBNETS.ns);
        }
      };

  private final Map<String, String> urlMappingTemplates = Collections.emptyMap();

  private final Map<SearchableResource, SearchableProvider.SearchResultHydrator>
      searchResultHydrators = Collections.emptyMap();

  public HuaweiCloudInfrastructureProvider(Collection<Agent> agents) {
    this.agents = agents;
  }

  @Override
  public String getProviderName() {
    return this.getClass().getName();
  }

  @Override
  public Collection<Agent> getAgents() {
    return agents;
  }

  @Override
  public Set<String> getDefaultCaches() {
    return defaultCaches;
  }

  @Override
  public Map<String, String> getUrlMappingTemplates() {
    return urlMappingTemplates;
  }

  @Override
  public Map<SearchableResource, SearchResultHydrator> getSearchResultHydrators() {
    return searchResultHydrators;
  }

  @Override
  public Map<String, String> parseKey(String key) {
    return Keys.parse(key);
  }
}
