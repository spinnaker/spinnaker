/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.cats.cache;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

/** Cache provides view access to data keyed by type and identifier. */
public interface Cache {
  enum StoreType {
    REDIS,
    IN_MEMORY,
    SQL
  }

  /**
   * Gets a single item from the cache by type and id
   *
   * @param type the type of the item
   * @param id the id of the item
   * @return the item matching the type and id
   */
  CacheData get(String type, String id);

  CacheData get(String type, String id, CacheFilter cacheFilter);

  /**
   * Determines if a specified id exists in the cache without loading the data.
   *
   * @param type the type of the item
   * @param identifier the id of the item
   * @return true iff the item is present in the cache
   */
  default boolean exists(String type, String identifier) {
    return !existingIdentifiers(type, Collections.singleton(identifier)).isEmpty();
  }

  /**
   * Filters the supplied list of identifiers to only those that exist in the cache.
   *
   * @param type the type of the item
   * @param identifiers the identifiers for the items
   * @return the list of identifiers that are present in the cache from the provided identifiers
   */
  default Collection<String> existingIdentifiers(String type, String... identifiers) {
    if (identifiers.length == 0) {
      return Collections.emptySet();
    }
    return existingIdentifiers(type, Arrays.asList(identifiers));
  }

  /**
   * Filters the supplied list of identifiers to only those that exist in the cache.
   *
   * @param type the type of the item
   * @param identifiers the identifiers for the items
   * @return the list of identifiers that are present in the cache from the provided identifiers
   */
  Collection<String> existingIdentifiers(String type, Collection<String> identifiers);

  /**
   * Retrieves all the identifiers for a type
   *
   * @param type the type for which to retrieve identifiers
   * @return the identifiers for the type
   */
  Collection<String> getIdentifiers(String type);

  /**
   * Returns the identifiers for the specified type that match the provided glob.
   *
   * @param type The type for which to retrieve identifiers
   * @param glob The glob to match against the identifiers
   * @return the identifiers for the type that match the glob
   */
  Collection<String> filterIdentifiers(String type, String glob);

  /**
   * Retrieves all the items for the specified type
   *
   * @param type the type for which to retrieve items
   * @return all the items for the type
   */
  Collection<CacheData> getAll(String type);

  Collection<CacheData> getAll(String type, CacheFilter cacheFilter);

  /**
   * Retrieves the items for the specified type matching the provided identifiers
   *
   * @param type the type for which to retrieve items
   * @param identifiers the identifiers
   * @return the items matching the type and identifiers
   */
  Collection<CacheData> getAll(String type, Collection<String> identifiers);

  Collection<CacheData> getAll(
      String type, Collection<String> identifiers, CacheFilter cacheFilter);

  /**
   * Retrieves the items for the specified type matching the provided identifiers
   *
   * @param type the type for which to retrieve items
   * @param identifiers the identifiers
   * @return the items matching the type and identifiers
   */
  Collection<CacheData> getAll(String type, String... identifiers);

  /**
   * Retrieves all items for the specified type associated with the provided application. Requires a
   * storeType with secondary indexes and support in the type's caching agent.
   *
   * @param type the type for which to retrieve items
   * @param application the application name
   * @return the matching items, keyed by type
   */
  default Map<String, Collection<CacheData>> getAllByApplication(String type, String application) {
    throw new UnsupportedCacheMethodException("Method only implemented for StoreType.SQL");
  }

  /**
   * Retrieves all items for the specified type associated with the provided application. Requires a
   * storeType with secondary indexes and support in the type's caching agent.
   *
   * @param type the type for which to retrieve items
   * @param application the application name
   * @param cacheFilter the cacheFilter to govern which relationships to fetch
   * @return the matching items, keyed by type
   */
  default Map<String, Collection<CacheData>> getAllByApplication(
      String type, String application, CacheFilter cacheFilter) {
    throw new UnsupportedCacheMethodException("Method only implemented for StoreType.SQL");
  }

  /**
   * Retrieves all items for the specified type associated with the provided application. Requires a
   * storeType with secondary indexes and support in the type's caching agent.
   *
   * @param types collection of types for which to retrieve items
   * @param application the application name
   * @param cacheFilters cacheFilters to govern which relationships to fetch, as type to filter
   * @return the matching items, keyed by type
   */
  default Map<String, Collection<CacheData>> getAllByApplication(
      Collection<String> types, String application, Map<String, CacheFilter> cacheFilters) {
    throw new UnsupportedCacheMethodException("Method only implemented for StoreType.SQL");
  }

  /**
   * Get backing store type for Cache implementation
   *
   * @return the backing StoreType
   */
  default StoreType storeType() {
    return StoreType.REDIS;
  }
}
