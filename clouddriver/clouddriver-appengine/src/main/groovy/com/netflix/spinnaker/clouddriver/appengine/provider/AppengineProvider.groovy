/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.appengine.provider

import com.netflix.spinnaker.cats.provider.ProviderCacheConfiguration
import com.netflix.spinnaker.clouddriver.appengine.AppengineCloudProvider
import com.netflix.spinnaker.clouddriver.appengine.cache.Keys
import com.netflix.spinnaker.clouddriver.cache.SearchableProvider
import com.netflix.spinnaker.clouddriver.security.BaseProvider

class AppengineProvider extends BaseProvider implements SearchableProvider, ProviderCacheConfiguration {
  public static final String PROVIDER_NAME = AppengineProvider.name

  /**
   * Every caching agent here always reports its authoritative namespace's key in the
   * CacheResult, even with an empty list when there's no live data this cycle, so the SQL
   * cache's existingIds-minus-currentIds eviction diff can always run. Without opting in here,
   * SqlCache's default safeguard against ever evicting the last item of a type discards that
   * entry before the diff can run, and the stale entry is never cleaned up.
   */
  @Override
  boolean supportsFullEviction() {
    return true
  }

  final Map<String, String> urlMappingTemplates = Collections.emptyMap()
  final Map<SearchableProvider.SearchableResource, SearchableProvider.SearchResultHydrator> searchResultHydrators = Collections.emptyMap()
  final AppengineCloudProvider cloudProvider
  final Set<String> defaultCaches = [
    Keys.Namespace.APPLICATIONS.ns,
    Keys.Namespace.CLUSTERS.ns,
    Keys.Namespace.SERVER_GROUPS.ns,
    Keys.Namespace.INSTANCES.ns,
    Keys.Namespace.LOAD_BALANCERS.ns,
  ].asImmutable()

  AppengineProvider(AppengineCloudProvider cloudProvider) {
    this.cloudProvider = cloudProvider
  }

  @Override
  String getProviderName() {
    return PROVIDER_NAME
  }

  @Override
  Map<String, String> parseKey(String key) {
    return Keys.parse(key)
  }
}
