/*
 * Copyright 2020 YANDEX LLC
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

package com.netflix.spinnaker.clouddriver.yandex.provider;

import com.netflix.spinnaker.cats.agent.Agent;
import com.netflix.spinnaker.cats.agent.AgentSchedulerAware;
import com.netflix.spinnaker.cats.cache.Cache;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.clouddriver.cache.SearchableProvider;
import java.util.*;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
public class YandexInfrastructureProvider extends AgentSchedulerAware
    implements SearchableProvider {
  private final Collection<Agent> agents;
  private final String providerName = YandexInfrastructureProvider.class.getName();
  private final Set<String> defaultCaches =
      new HashSet<>(
          Arrays.asList(
              Keys.Namespace.APPLICATIONS.getNs(),
              Keys.Namespace.CLUSTERS.getNs(),
              Keys.Namespace.INSTANCES.getNs(),
              Keys.Namespace.LOAD_BALANCERS.getNs(),
              Keys.Namespace.SERVER_GROUPS.getNs()));
  private final Map<String, String> urlMappingTemplates =
      Collections.singletonMap(Keys.Namespace.CLUSTERS.getNs(), "/serverGroups/$name");
  //      (SECURITY_GROUPS.ns): '/securityGroups/$account/$provider/$name?region=$region'

  //  final Map<String, String> urlMappingTemplates = [
  //    (Keys.Namespace.SERVER_GROUPS.ns) :
  // '/applications/${application.toLowerCase()}/clusters/$account/$cluster/$provider/serverGroups/$serverGroup?region=$region',
  //    (Keys.Namespace.LOAD_BALANCERS.ns): '/$provider/loadBalancers/$loadBalancer',
  //    (Keys.Namespace.CLUSTERS.ns)      :
  // '/applications/${application.toLowerCase()}/clusters/$account/$cluster'
  //    ].asImmutable()

  //  final Map<String, String> urlMappingTemplates = [
  //    (SERVER_GROUPS.ns) :
  // '/applications/${application.toLowerCase()}/clusters/$account/$cluster/$provider/serverGroups/$serverGroup?region=$region',
  //    (LOAD_BALANCERS.ns): '/$provider/loadBalancers/$loadBalancer',
  //    (CLUSTERS.ns)      : '/applications/${application.toLowerCase()}/clusters/$account/$cluster'
  //    ].asImmutable()

  private final Map<SearchableResource, SearchResultHydrator> searchResultHydrators =
      new HashMap<>();

  public YandexInfrastructureProvider(Collection<Agent> agents) {
    this.agents = agents;
    registerHydrator(Keys.Namespace.INSTANCES);
    registerHydrator(Keys.Namespace.SERVER_GROUPS);
    registerHydrator(Keys.Namespace.CLUSTERS);
  }

  private void registerHydrator(Keys.Namespace ns) {
    searchResultHydrators.put(
        new SearchableResource(ns.getNs().toLowerCase(), "yandex"),
        new NamespaceResultHydrator(ns));
  }

  @Override
  public Map<String, String> parseKey(String key) {
    return Keys.parse(key);
  }

  @AllArgsConstructor
  private static class NamespaceResultHydrator implements SearchResultHydrator {
    Keys.Namespace namespace;

    @Override
    public Map<String, String> hydrateResult(
        Cache cacheView, Map<String, String> result, String id) {
      Map<String, String> hydrated = new HashMap<>(result);
      Optional.ofNullable(cacheView.get(namespace.getNs(), id))
          .map(CacheData::getRelationships)
          .map(r -> Keys.parse(r.get(Keys.Namespace.CLUSTERS.getNs()).iterator().next()))
          .ifPresent(
              cluster -> {
                hydrated.put("application", cluster.get("application"));
                hydrated.put("cluster", cluster.get("cluster"));
              });
      return hydrated;
    }
  }
}
