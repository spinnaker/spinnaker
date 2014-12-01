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

package com.netflix.spinnaker.cats.mem;

import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.DefaultCacheData;
import com.netflix.spinnaker.cats.cache.WriteableCache;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * A WriteableCache that stores objects in an in-memory map.
 */
public class InMemoryCache implements WriteableCache {
    private ConcurrentMap<String, ConcurrentMap<String, CacheData>> typeMap = new ConcurrentHashMap<>();

    @Override
    public void merge(String type, CacheData cacheData) {
        merge(getOrCreate(type, cacheData.getId()), cacheData);
    }

    @Override
    public void mergeAll(String type, Collection<CacheData> items) {
        for (CacheData item : items) {
            merge(type, item);
        }
    }

    @Override
    public void evict(String type, String id) {
        getTypeMap(type).remove(id);
    }

    @Override
    public void evictAll(String type, Collection<String> ids) {
        ConcurrentMap<String, CacheData> map = getTypeMap(type);
        for (String id : ids) {
            map.remove(id);
        }
    }

    @Override
    public CacheData get(String type, String id) {
        CacheData existing = getTypeMap(type).get(id);
        if (existing != null) {
            return wrap(existing);
        }
        return null;
    }

    @Override
    public Collection<CacheData> getAll(String type) {
        ConcurrentMap<String, CacheData> map = getTypeMap(type);
        Collection<CacheData> values = new LinkedList<>();
        for (CacheData data : map.values()) {
            CacheData toReturn = wrap(data);
            if (toReturn != null) {
                values.add(wrap(data));
            }
        }
        return values;
    }

    @Override
    public Collection<CacheData> getAll(String type, Collection<String> identifiers) {
        ConcurrentMap<String, CacheData> map = getTypeMap(type);
        Collection<CacheData> values = new ArrayList<>(identifiers.size());
        for (String id : identifiers) {
            CacheData toReturn = wrap(map.get(id));
            if (toReturn != null) {
                values.add(toReturn);
            }
        }
        return values;
    }

    @Override
    public Collection<CacheData> getAll(String type, String... identifiers) {
        return getAll(type, Arrays.asList(identifiers));
    }

    public Collection<String> getIdentifiers(String type) {
        return new HashSet<>(getTypeMap(type).keySet());
    }

    public Collection<String> getIdentifiers(String type, String filter) {
        HashSet<String> matches = new HashSet<>();
        for (String key : getTypeMap(type).keySet()) {
            if (key.contains(filter)) {
                matches.add(key);
            }
        }
        return matches;
    }

    private CacheData getOrCreate(String type, String id) {
        return getCacheData(getTypeMap(type), id);
    }

    private ConcurrentMap<String, CacheData> getTypeMap(String type) {
        ConcurrentMap<String, CacheData> newValue = new ConcurrentHashMap<>();
        ConcurrentMap<String, CacheData> existing = typeMap.putIfAbsent(type, newValue);
        if (existing == null) {
            return newValue;
        }

        return existing;
    }

    private CacheData wrap(CacheData data) {
        if (data == null || data.getAttributes().isEmpty()) {
            return null;
        }
        return new DefaultCacheData(data.getId(), data.getAttributes(), data.getRelationships());
    }

    private CacheData getCacheData(ConcurrentMap<String, CacheData> map, String id) {
        CacheData newValue = new BackingData(id);
        CacheData existing = map.putIfAbsent(id, newValue);
        if (existing == null) {
            return newValue;
        }

        return existing;
    }

    private void merge(CacheData existing, CacheData update) {
        MapMutation<String, Object> attributes = new MapMutation<>(update.getAttributes());
        MapMutation<String, Collection<String>> relationships = new MapMutation<>(update.getRelationships());

        Set<String> missingAttributes = new HashSet<>(existing.getAttributes().keySet());
        missingAttributes.removeAll(update.getAttributes().keySet());
        attributes.apply(existing.getAttributes());
        existing.getAttributes().keySet().removeAll(missingAttributes);
        relationships.apply(existing.getRelationships());
    }

    /**
     * ConcurrentHashMap doesn't support null values, this translates a sourceMap into
     * a combination of non-null update values and a set of keys to remove
     *
     * @param <K> the key type
     * @param <V> the value type
     */
    private static class MapMutation<K, V> {
        private final Map<K, V> updateData;
        private final Set<K> removalSet;

        public MapMutation(Map<K, V> source) {
            Map<K, V> toPut = new HashMap<>();
            Set<K> toRemove = new HashSet<>();
            for (Map.Entry<K, V> entry : source.entrySet()) {
                if (entry.getValue() == null) {
                    toRemove.add(entry.getKey());
                } else {
                    toPut.put(entry.getKey(), entry.getValue());
                }
            }
            updateData = Collections.unmodifiableMap(toPut);
            removalSet = Collections.unmodifiableSet(toRemove);
        }

        public void apply(Map<K, V> target) {
            target.putAll(updateData);
            target.keySet().removeAll(removalSet);
        }
    }

    private static class BackingData implements CacheData {
        private final ConcurrentMap<String, Object> attributes = new ConcurrentHashMap<>();
        private final ConcurrentMap<String, Collection<String>> relationships = new ConcurrentHashMap<>();
        private final String id;

        public BackingData(String id) {
            this.id = id;
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public Map<String, Object> getAttributes() {
            return attributes;
        }

        @Override
        public Map<String, Collection<String>> getRelationships() {
            return relationships;
        }
    }
}
