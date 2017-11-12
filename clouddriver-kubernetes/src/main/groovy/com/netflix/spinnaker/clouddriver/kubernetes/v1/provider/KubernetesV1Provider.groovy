/*
 * Copyright 2017 Google, Inc.
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
 *
 */

package com.netflix.spinnaker.clouddriver.kubernetes.v1.provider

import com.netflix.spinnaker.cats.agent.Agent
import com.netflix.spinnaker.cats.agent.AgentSchedulerAware
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.clouddriver.cache.SearchableProvider
import com.netflix.spinnaker.clouddriver.kubernetes.KubernetesCloudProvider
import com.netflix.spinnaker.clouddriver.kubernetes.v1.caching.Keys
import groovy.util.logging.Slf4j

import static com.netflix.spinnaker.clouddriver.cache.SearchableProvider.SearchableResource

@Slf4j
class KubernetesV1Provider extends AgentSchedulerAware implements SearchableProvider {
  public static final String PROVIDER_NAME = KubernetesV1Provider.name

  final Map<String, String> urlMappingTemplates = Collections.emptyMap()

  final Collection<Agent> agents
  final KubernetesCloudProvider cloudProvider

  KubernetesV1Provider(KubernetesCloudProvider cloudProvider, Collection<Agent> agents) {
    this.cloudProvider = cloudProvider
    this.agents = agents
  }

  final Set<String> defaultCaches = [
      Keys.Namespace.LOAD_BALANCERS.ns,
      Keys.Namespace.CLUSTERS.ns,
      Keys.Namespace.SERVER_GROUPS.ns,
      Keys.Namespace.INSTANCES.ns,
      Keys.Namespace.SECURITY_GROUPS.ns,
      Keys.Namespace.SERVICE_ACCOUNTS.ns,
      Keys.Namespace.CONFIG_MAPS.ns,
      Keys.Namespace.SECRETS.ns,
  ].asImmutable()

  @Override
  String getProviderName() {
    return PROVIDER_NAME
  }

  final Map<SearchableResource, SearchableProvider.SearchResultHydrator> searchResultHydrators = [
    (new KubernetesSearchableResource(Keys.Namespace.SERVICE_ACCOUNTS.ns)): new ServiceAccountResultHydrator(),
    (new KubernetesSearchableResource(Keys.Namespace.CONFIG_MAPS.ns)): new ConfigMapResultHydrator(),
    (new KubernetesSearchableResource(Keys.Namespace.SECRETS.ns)): new SecretResultHydrator(),
  ]

  @Override
  Map<String, String> parseKey(String key) {
    return Keys.parse(key)
  }

  private static class KubernetesSearchableResource extends SearchableResource {
    public KubernetesSearchableResource(String resourceType) {
      this.resourceType = resourceType.toLowerCase()
      this.platform = "kubernetes"
    }
  }

  private static class ServiceAccountResultHydrator implements SearchableProvider.SearchResultHydrator {

    @Override
    Map<String,String> hydrateResult(Cache cacheView, Map<String,String> result, String id) {
      CacheData sa = cacheView.get(Keys.Namespace.SERVICE_ACCOUNTS.ns, id)
      return result + [
        name: sa.attributes.name as String,
        namespace: sa.attributes.namespace as String
      ]
    }
  }

  private static class ConfigMapResultHydrator implements SearchableProvider.SearchResultHydrator {

    @Override
    Map<String,String> hydrateResult(Cache cacheView, Map<String,String> result, String id) {
      CacheData cm = cacheView.get(Keys.Namespace.CONFIG_MAPS.ns, id)
      return result + [
        name: cm.attributes.name as String,
        namespace: cm.attributes.namespace as String
      ]
    }
  }

  private static class SecretResultHydrator implements SearchableProvider.SearchResultHydrator {

    @Override
    Map<String,String> hydrateResult(Cache cacheView, Map<String,String> result, String id) {
      CacheData secret = cacheView.get(Keys.Namespace.SECRETS.ns, id)
      return result + [
        name: secret.attributes.name as String,
        namespace: secret.attributes.namespace as String
      ]
    }
  }
}
