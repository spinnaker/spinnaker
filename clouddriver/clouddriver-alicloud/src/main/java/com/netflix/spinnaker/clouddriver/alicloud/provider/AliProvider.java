/*
 * Copyright 2019 Alibaba Group.
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
package com.netflix.spinnaker.clouddriver.alicloud.provider;

import static com.netflix.spinnaker.clouddriver.alicloud.cache.Keys.Namespace.SECURITY_GROUPS;
import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.*;

import com.netflix.spinnaker.cats.agent.Agent;
import com.netflix.spinnaker.cats.agent.AgentSchedulerAware;
import com.netflix.spinnaker.clouddriver.alicloud.cache.Keys;
import com.netflix.spinnaker.clouddriver.cache.SearchableProvider;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@ConditionalOnProperty("alicloud.enabled")
public class AliProvider extends AgentSchedulerAware implements SearchableProvider {
  public static final String PROVIDER_NAME = AliProvider.class.getName();

  private final AccountCredentialsRepository accountCredentialsRepository;
  private final Collection<Agent> agents;

  AliProvider(AccountCredentialsRepository accountCredentialsRepository, Collection<Agent> agents) {
    this.accountCredentialsRepository = accountCredentialsRepository;
    this.agents = agents;
  }

  @Override
  public String getProviderName() {
    return PROVIDER_NAME;
  }

  @Override
  public Collection<Agent> getAgents() {
    return agents;
  }

  final Set<String> defaultCaches =
      new HashSet<String>() {
        {
          add(LOAD_BALANCERS.ns);
          add(CLUSTERS.ns);
          add(SERVER_GROUPS.ns);
          add(TARGET_GROUPS.ns);
          add(INSTANCES.ns);
          add(SECURITY_GROUPS.ns);
        }
      };

  final Map<String, String> urlMappingTemplates =
      new HashMap<String, String>() {
        {
          put(
              SERVER_GROUPS.ns,
              "/applications/${application.toLowerCase()}/clusters/$account/$cluster/$provider/serverGroups/$serverGroup?region=$region");
          put(LOAD_BALANCERS.ns, "/$provider/loadBalancers/$loadBalancer");
          put(CLUSTERS.ns, "/applications/${application.toLowerCase()}/clusters/$account/$cluster");
          put(SECURITY_GROUPS.ns, "/securityGroups/$account/$provider/$name?region=$region");
        }
      };

  final Map<SearchableResource, SearchResultHydrator> searchResultHydrators =
      Collections.emptyMap();

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
