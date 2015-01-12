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

import java.util.Collection;

/**
 * Cache provides view access to data keyed by type and identifier.
 */
public interface Cache {
    /**
     * Gets a single item from the cache by type and id
     * @param type the type of the item
     * @param id the id of the item
     * @return the item matching the type and id
     */
    CacheData get(String type, String id);

    CacheData get(String type, String id, CacheFilter cacheFilter);

    /**
     * Retrieves all the identifiers for a type
     * @param type the type for which to retrieve identifiers
     * @return the identifiers for the type
     */
    Collection<String> getIdentifiers(String type);

    /**
     * Returns the identifiers for the specified type that match the provided glob.
     * @param type The type for which to retrieve identifiers
     * @param glob The glob to match against the identifiers
     * @return the identifiers for the type that match the glob
     */
    Collection<String> filterIdentifiers(String type, String glob);

    /**
     * Retrieves all the items for the specified type
     * @param type the type for which to retrieve items
     * @return all the items for the type
     */
    Collection<CacheData> getAll(String type);

    Collection<CacheData> getAll(String type, CacheFilter cacheFilter);

    /**
     * Retrieves the items for the specified type matching the provided identifiers
     * @param type the type for which to retrieve items
     * @param identifiers the identifiers
     * @return the items matching the type and identifiers
     */
    Collection<CacheData> getAll(String type, Collection<String> identifiers);

    Collection<CacheData> getAll(String type, Collection<String> identifiers, CacheFilter cacheFilter);

    /**
     * Retrieves the items for the specified type matching the provided identifiers
     * @param type the type for which to retrieve items
     * @param identifiers the identifiers
     * @return the items matching the type and identifiers
     */
    Collection<CacheData> getAll(String type, String... identifiers);
}
