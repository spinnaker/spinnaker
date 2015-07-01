/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.cache

import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.provider.Provider

interface SearchableProvider extends Provider {

  /**
   * Names of caches to search by default
   */
  Set<String> getDefaultCaches()

  /**
   * Map keyed by named cache to a template that produces a url for a search result.
   *
   * The template will be supplied the result from calling parseKey on the search key
   */
  Map<String, String> getUrlMappingTemplates()

  /**
   * SearchResultHydrators for cache types
   */
  Map<String, SearchResultHydrator> getSearchResultHydrators()

  /**
   * IdentifierExtractors for cache types
   */
  Map<String, IdentifierExtractor> getIdentifierExtractors()

  /**
   * The parts of the key, if this Provider supports keys of this type, otherwise null.
   */
  Map<String, String> parseKey(String key)

  /**
   * A SearchResultHydrator provides a custom strategy for enhancing result data for a particular cache type.
   */
  public static interface SearchResultHydrator {
    Map<String, String> hydrateResult(Cache cacheView, Map<String, String> result, String id)
  }

  /**
   * An IdentifierExtractor provides a custom strategy for interpreting a query for a particular cache type.
   */
  public static interface IdentifierExtractor {
    Collection<String> getIdentifiers(Cache cacheView, String type, String query)
  }
}
