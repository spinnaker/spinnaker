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

/**
 * A cache that provides a unified view of multiples, merging items from each
 * cache together.
 */
public class CompositeCache implements Cache {

    private final Collection<? extends Cache> caches;

    public CompositeCache(Collection<? extends Cache> caches) {
        this.caches = caches;
    }

    @Override
    public CacheData get(String type, String id) {
        Collection<CacheData> elements = new ArrayList<>(caches.size());
        for (Cache cache : caches) {
            CacheData element = cache.get(type, id);
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
        Map<String, CacheData> allItems = new HashMap<>();
        for (Cache cache : caches) {
            Collection<CacheData> all = cache.getAll(type);
            for (CacheData item : all) {
                CacheData existing = allItems.get(item.getId());
                if (existing == null) {
                    allItems.put(item.getId(), item);
                } else {
                    allItems.put(item.getId(), merge(item.getId(), existing, item));
                }
            }
        }
        return allItems.values();
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
