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

package com.netflix.spinnaker.cats.provider;

import com.netflix.spinnaker.cats.agent.CacheResult;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.DefaultCacheData;
import com.netflix.spinnaker.cats.cache.WriteableCache;

import java.util.*;

/**
 * An implementation of ProviderCache that writes through to a provided backing
 * WriteableCache.
 *
 * This implementation will handle aggregating results from multiple sources, and
 * the view methods will merge relationships from all sources into a single relationship.
 */
public class DefaultProviderCache implements ProviderCache {

    private static final String ALL_ID = "_ALL_"; //dirty = true

    private final WriteableCache backingStore;

    public DefaultProviderCache(WriteableCache backingStore) {
        this.backingStore = backingStore;
    }

    @Override
    public CacheData get(String type, String id) {
        validateTypes(type);
        if (ALL_ID.equals(id)) {
            return null;
        }
        CacheData item = backingStore.get(type, id);
        if (item == null) {
            return null;
        }

        return mergeRelationships(item);
    }

    @Override
    public Collection<CacheData> getAll(String type) {
        validateTypes(type);
        Collection<CacheData> all = backingStore.getAll(type);
        Collection<CacheData> response = new ArrayList<>(all.size());
        for (CacheData item : all) {
            if (!ALL_ID.equals(item.getId())) {
                response.add(mergeRelationships(item));
            }
        }
        return Collections.unmodifiableCollection(response);
    }

    @Override
    public Collection<String> getIdentifiers(String type) {
        validateTypes(type);
        return backingStore.getIdentifiers(type);
    }

    @Override
    public void putCacheResult(String sourceAgentType, Collection<String> authoritativeTypes, CacheResult cacheResult) {
        Set<String> allTypes = new HashSet<>(cacheResult.getCacheResults().keySet());
        allTypes.addAll(authoritativeTypes);
        validateTypes(allTypes);

        for (String type : allTypes) {
            final Collection<String> previousSet;
            if (authoritativeTypes.contains(type)) {
                previousSet = getExistingSourceIdentifiers(type, sourceAgentType);
            } else {
                previousSet = Collections.emptySet();
            }
            if (cacheResult.getCacheResults().containsKey(type)) {
                cacheDataType(type, sourceAgentType, cacheResult.getCacheResults().get(type));
                for (CacheData data : cacheResult.getCacheResults().get(type)) {
                    previousSet.remove(data.getId());
                }
            }
            evictDeletedItems(type, previousSet);
        }
    }

    private void validateTypes(String... types) {
        validateTypes(Arrays.asList(types));
    }

    private void validateTypes(Collection<String> types) {
        Set<String> invalid = new HashSet<>();
        for (String type : types) {
            if (!validType(type)) {
                invalid.add(type);
            }
        }
        if (!invalid.isEmpty()) {
            throw new IllegalArgumentException("Types contain unsupported characters: " + invalid);
        }
    }

    private boolean validType(String type) {
        return type.indexOf(':') == -1;
    }

    private Collection<String> getExistingSourceIdentifiers(String type, String sourceAgentType) {
        CacheData all = backingStore.get(type, ALL_ID);
        if (all == null) {
            return Collections.emptySet();
        }
        Collection<String> relationship = all.getRelationships().get(sourceAgentType);
        if (relationship == null) {
            return Collections.emptySet();
        }
        return relationship;
    }

    private void cacheDataType(String type, String sourceAgentType, Collection<CacheData> items) {
        Collection<String> idSet = new HashSet<>();

        Collection<CacheData> toStore = new ArrayList<>(items.size() + 1);
        for (CacheData item : items) {
            idSet.add(item.getId());
            toStore.add(uniqueifyRelationships(item, sourceAgentType));
        }
        Map<String, Collection<String>> allRelationship = new HashMap<>();
        allRelationship.put(sourceAgentType, idSet);
        toStore.add(new DefaultCacheData(ALL_ID, Collections.<String, Object>emptyMap(), allRelationship));

        backingStore.mergeAll(type, toStore);
    }

    private CacheData uniqueifyRelationships(CacheData source, String sourceAgentType) {
        Map<String, Collection<String>> relationships = new HashMap<>(source.getRelationships().size());
        for (Map.Entry<String, Collection<String>> entry : source.getRelationships().entrySet()) {
            relationships.put(entry.getKey() + ':' + sourceAgentType, entry.getValue());
        }
        return new DefaultCacheData(source.getId(), source.getAttributes(), relationships);
    }

    private CacheData mergeRelationships(CacheData source) {
        Map<String, Collection<String>> relationships = new HashMap<>(source.getRelationships().size());
        for (Map.Entry<String, Collection<String>> entry : source.getRelationships().entrySet()) {
            int idx = entry.getKey().indexOf(':');
            if (idx == -1) {
                throw new IllegalStateException("Expected delimiter in relationship key");
            }
            String type = entry.getKey().substring(0, idx);
            Collection<String> values = relationships.get(type);
            if (values == null) {
                values = new HashSet<>();
                relationships.put(type, values);
            }
            values.addAll(entry.getValue());
        }
        return new DefaultCacheData(source.getId(), source.getAttributes(), relationships);
    }

    @Override
    public void evictDeletedItems(String type, Collection<String> ids) {
        backingStore.evictAll(type, ids);
    }
}
