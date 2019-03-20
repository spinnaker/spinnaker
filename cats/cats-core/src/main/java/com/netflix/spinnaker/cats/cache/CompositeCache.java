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

import java.util.*;
import java.util.stream.Collectors;

/**
 * A cache that provides a unified view of multiples, merging items from each
 * cache together.
 */
public class CompositeCache implements Cache {

    private final Collection<? extends Cache> caches;

    public CompositeCache(Collection<? extends Cache> caches) {
        this.caches = caches;
    }

    public Set<StoreType> getStoreTypes() {
        return caches.stream()
          .map(c -> ((Cache) c).storeType())
          .collect(Collectors.toSet());
    }

    @Override
    public CacheData get(String type, String id) {
        return get(type, id, null);
    }

    @Override
    public CacheData get(String type, String id, CacheFilter cacheFilter) {
        Collection<CacheData> elements = new ArrayList<>(caches.size());
        for (Cache cache : caches) {
            CacheData element = cache.get(type, id, cacheFilter);
            if (element != null) {
                elements.add(element);
            }
        }
        if (elements.isEmpty()) {
            return null;
        }
        return merge(id, elements);
    }

    @Override
    public Collection<CacheData> getAll(String type) {
        return getAll(type, (CacheFilter) null);

    }

    @Override
    public Collection<CacheData> getAll(String type, CacheFilter cacheFilter) {
        Map<String, CacheData> allItems = new HashMap<>();
        for (Cache cache : caches) {
            allItems = merge(allItems, cache.getAll(type, cacheFilter));
        }
        return allItems.values();
    }

    @Override
    public Collection<String> existingIdentifiers(String type, Collection<String> ids) {
        HashSet<String> identifiers = new HashSet<>(ids.size());
        HashSet<String> remainingIds = new HashSet<>(ids);
        for (Cache cache : caches) {
          Collection<String> existing = cache.existingIdentifiers(type, remainingIds);
          identifiers.addAll(existing);

          //minimize redis exists calls - if we've seen the identifier in at least
          // one cache, then we know it exists
          remainingIds.removeAll(existing);
          if (remainingIds.isEmpty()) {
            break;
          }
        }
        return identifiers;
    }

    @Override
    public Collection<String> getIdentifiers(String type) {
        HashSet<String> identifiers = new HashSet<>();
        for (Cache cache : caches) {
            identifiers.addAll(cache.getIdentifiers(type));
        }
        return identifiers;
    }

    @Override
    public Collection<String> filterIdentifiers(String type, String glob) {
        HashSet<String> identifiers = new HashSet<>();
        for (Cache cache : caches) {
            identifiers.addAll(cache.filterIdentifiers(type, glob));
        }
        return identifiers;
    }

    @Override
    public Collection<CacheData> getAll(String type, Collection<String> identifiers) {
        return getAll(type, identifiers, null);
    }

    @Override
    public Collection<CacheData> getAll(String type, Collection<String> identifiers, CacheFilter cacheFilter) {
        Map<String, CacheData> allItems = new HashMap<>();
        for (Cache cache : caches) {
            allItems = merge(allItems, cache.getAll(type, identifiers, cacheFilter));
        }
        return allItems.values();
    }

    @Override
    public Collection<CacheData> getAll(String type, String... identifiers) {
        return getAll(type, Arrays.asList(identifiers));
    }

    @Override
    public Map<String, Collection<CacheData>> getAllByApplication(String type, String application) {
        Map<String, Collection<CacheData>> allItems = new HashMap<>();
        for (Cache cache : caches) {
            allItems.putAll(cache.getAllByApplication(type, application));
        }
        return allItems;
    }

    @Override
    public Map<String, Collection<CacheData>> getAllByApplication(String type,
                                                                  String application,
                                                                  CacheFilter filter) {
        Map<String, Collection<CacheData>> allItems = new HashMap<>();
        for (Cache cache : caches) {
            allItems.putAll(cache.getAllByApplication(type, application, filter));
        }
        return allItems;
    }

    @Override
    public Map<String, Collection<CacheData>> getAllByApplication(Collection<String> types,
                                                                  String application,
                                                                  Map<String, CacheFilter> cacheFilters) {
        Map<String, Collection<CacheData>> allItems = new HashMap<>();
        for (Cache cache : caches) {
            allItems.putAll(cache.getAllByApplication(types, application, cacheFilters));
        }
        return allItems;
    }

    Map<String, CacheData> merge(Map<String, CacheData> existingItems, Collection<CacheData> results) {
        final Map<String, CacheData> allItems = existingItems == null ? new HashMap<String, CacheData>() : existingItems;
        for (CacheData item : results) {
            CacheData existing = allItems.get(item.getId());
            if (existing == null) {
                allItems.put(item.getId(), item);
            } else {
                allItems.put(item.getId(), merge(item.getId(), existing, item));
            }
        }

        return allItems;
    }

    CacheData merge(String id, CacheData... elements) {
        return merge(id, Arrays.asList(elements));
    }

    CacheData merge(String id, Collection<CacheData> elements) {
        Map<String, Object> attributes = new HashMap<>();
        Map<String, Collection<String>> relationships = new HashMap<>();
        for (CacheData data : elements) {
            attributes.putAll(data.getAttributes());
            for (Map.Entry<String, Collection<String>> relationship : data.getRelationships().entrySet()) {
                Collection<String> existing = relationships.get(relationship.getKey());
                if (existing == null) {
                    existing = new HashSet<>();
                    relationships.put(relationship.getKey(), existing);
                }
                existing.addAll(relationship.getValue());
            }
        }
        return new DefaultCacheData(id, attributes, relationships);
    }
}
