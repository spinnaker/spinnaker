/*
 * Copyright 2018 Pivotal, Inc.
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
 */

package com.netflix.spinnaker.clouddriver.cloudfoundry.provider;

import static com.netflix.spinnaker.clouddriver.cloudfoundry.cache.Keys.Namespace.*;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonMap;
import static java.util.stream.Collectors.toSet;

import com.netflix.spinnaker.cats.agent.Agent;
import com.netflix.spinnaker.cats.agent.AgentSchedulerAware;
import com.netflix.spinnaker.cats.cache.Cache;
import com.netflix.spinnaker.clouddriver.cache.SearchableProvider;
import com.netflix.spinnaker.clouddriver.cloudfoundry.cache.Keys;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public class CloudFoundryProvider extends AgentSchedulerAware implements SearchableProvider {
  private final Set<String> defaultCaches =
      Stream.of(
              APPLICATIONS.getNs(),
              CLUSTERS.getNs(),
              SERVER_GROUPS.getNs(),
              INSTANCES.getNs(),
              LOAD_BALANCERS.getNs())
          .collect(toSet());

  private final Map<SearchableResource, SearchResultHydrator> searchResultHydrators =
      singletonMap(
          new SearchableResource(APPLICATIONS.getNs(), "cloudfoundry"),
          new ApplicationSearchResultHydrator());

  private final Map<String, String> urlMappingTemplates = emptyMap();
  public static final String PROVIDER_ID = "cloudfoundry";
  private final String providerName = CloudFoundryProvider.class.getName();

  private final Collection<Agent> agents;

  static class ApplicationSearchResultHydrator implements SearchableProvider.SearchResultHydrator {
    @Override
    public Map<String, String> hydrateResult(
        Cache cacheView, Map<String, String> result, String id) {
      // needed by deck to render correctly in infrastructure search results
      result.put("application", result.get("name"));
      return result;
    }
  }

  @Nullable
  @Override
  public Map<String, String> parseKey(String key) {
    return Keys.parse(key).orElse(null);
  }
}
