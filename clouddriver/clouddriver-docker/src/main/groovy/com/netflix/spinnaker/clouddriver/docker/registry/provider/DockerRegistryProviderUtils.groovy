/*
 * Copyright 2016 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.clouddriver.docker.registry.provider

import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter

class DockerRegistryProviderUtils {
  static Set<CacheData> getAllMatchingKeyPattern(Cache cacheView, String namespace, String pattern) {
    loadResults(cacheView, namespace, cacheView.filterIdentifiers(namespace, pattern))
  }

  static Set<CacheData> loadResults(Cache cacheView, String namespace, Collection<String> identifiers) {
    cacheView.getAll(namespace, identifiers, RelationshipCacheFilter.none())
  }

  static imageId(String registry, String repository, String tag) {
    "$registry/$repository:$tag"
  }
}
