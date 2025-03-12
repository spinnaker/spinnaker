/*
 * Copyright 2019 THL A29 Limited, a Tencent company.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.tencentcloud.provider;

import static com.netflix.spinnaker.clouddriver.tencentcloud.cache.Keys.Namespace.APPLICATIONS;
import static com.netflix.spinnaker.clouddriver.tencentcloud.cache.Keys.Namespace.CLUSTERS;
import static com.netflix.spinnaker.clouddriver.tencentcloud.cache.Keys.Namespace.INSTANCES;
import static com.netflix.spinnaker.clouddriver.tencentcloud.cache.Keys.Namespace.LOAD_BALANCERS;
import static com.netflix.spinnaker.clouddriver.tencentcloud.cache.Keys.Namespace.SECURITY_GROUPS;
import static com.netflix.spinnaker.clouddriver.tencentcloud.cache.Keys.Namespace.SERVER_GROUPS;

import com.netflix.spinnaker.cats.agent.Agent;
import com.netflix.spinnaker.cats.agent.AgentSchedulerAware;
import com.netflix.spinnaker.clouddriver.cache.SearchableProvider;
import com.netflix.spinnaker.clouddriver.tencentcloud.cache.Keys;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Getter;

@Getter
public class TencentCloudInfrastructureProvider extends AgentSchedulerAware
    implements SearchableProvider {

  private final String providerName;
  private final List<Agent> agents;
  private final Set<String> defaultCaches;
  private final Map<String, String> urlMappingTemplates;
  private final Map<SearchableResource, SearchResultHydrator> searchResultHydrators;

  public TencentCloudInfrastructureProvider(List<Agent> agents) {
    this.providerName = TencentCloudInfrastructureProvider.class.getName();
    this.agents = agents;

    List<String> nsList =
        Arrays.asList(
            APPLICATIONS.ns,
            CLUSTERS.ns,
            INSTANCES.ns,
            LOAD_BALANCERS.ns,
            SECURITY_GROUPS.ns,
            SERVER_GROUPS.ns);

    this.defaultCaches = new HashSet<>();
    this.defaultCaches.addAll(nsList);

    this.urlMappingTemplates = new HashMap<>();
    this.urlMappingTemplates.put(
        SECURITY_GROUPS.ns, "/securityGroups/$account/$provider/$name?region=$region");
    this.searchResultHydrators = new HashMap<>();
  }

  @Override
  public Map<String, String> parseKey(String key) {
    return Keys.parse(key);
  }
}
