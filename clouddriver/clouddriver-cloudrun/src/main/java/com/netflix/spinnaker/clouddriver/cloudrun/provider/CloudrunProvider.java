/*
 * Copyright 2022 OpsMx Inc.
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

package com.netflix.spinnaker.clouddriver.cloudrun.provider;

import com.netflix.spinnaker.clouddriver.cache.SearchableProvider;
import com.netflix.spinnaker.clouddriver.cloudrun.CloudrunCloudProvider;
import com.netflix.spinnaker.clouddriver.cloudrun.cache.Keys;
import com.netflix.spinnaker.clouddriver.security.BaseProvider;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

public class CloudrunProvider extends BaseProvider implements SearchableProvider {
  public static final String PROVIDER_NAME = CloudrunProvider.class.getName();

  final Map<String, String> urlMappingTemplates = Collections.emptyMap();
  final Map<SearchableResource, SearchResultHydrator> searchResultHydrators =
      Collections.emptyMap();
  final CloudrunCloudProvider cloudProvider;
  String[] allKeys = {
    Keys.Namespace.APPLICATIONS.getNs(),
    Keys.Namespace.CLUSTERS.getNs(),
    Keys.Namespace.SERVER_GROUPS.getNs(),
    Keys.Namespace.INSTANCES.getNs(),
    Keys.Namespace.LOAD_BALANCERS.getNs()
  };

  final Set<String> defaultCaches = Set.of(allKeys);

  public CloudrunProvider(CloudrunCloudProvider cloudProvider) {
    this.cloudProvider = cloudProvider;
  }

  @Override
  public String getProviderName() {
    return PROVIDER_NAME;
  }

  @Override
  public Set<String> getDefaultCaches() {
    return defaultCaches;
  }

  /**
   * @return
   */
  @Override
  public Map<String, String> getUrlMappingTemplates() {
    return urlMappingTemplates;
  }

  /**
   * @return
   */
  @Override
  public Map<SearchableResource, SearchResultHydrator> getSearchResultHydrators() {
    return searchResultHydrators;
  }

  @Override
  public Map<String, String> parseKey(String key) {
    return Keys.parse(key);
  }
}
