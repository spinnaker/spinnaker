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

import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.cats.agent.CacheResult;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.CacheFilter;
import com.netflix.spinnaker.cats.cache.DefaultCacheData;
import com.netflix.spinnaker.cats.cache.WriteableCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * An implementation of ProviderCache that writes through to a provided backing
 * WriteableCache.
 *
 * This implementation will handle aggregating results from multiple sources, and
 * the view methods will merge relationships from all sources into a single relationship.
 */
public class DefaultProviderCache implements ProviderCache {

    private final Logger log = LoggerFactory.getLogger(DefaultProviderCache.class);

    private static final String ALL_ID = "_ALL_"; //dirty = true
    private static final Map<String, Object> ALL_ATTRIBUTE = Collections.unmodifiableMap(new HashMap<String, Object>(1) {{
        put("id", ALL_ID);
    }});

    private final WriteableCache backingStore;
    private final Registry registry;

    public DefaultProviderCache(WriteableCache backingStore, Registry registry) {
        this.backingStore = backingStore;
        this.registry = registry;
    }

    @Override
    public CacheData get(String type, String id) {
        return get(type, id, null);
    }

    @Override
    public CacheData get(String type, String id, CacheFilter cacheFilter) {
        validateTypes(type);
        if (ALL_ID.equals(id)) {
            return null;
        }
        CacheData item = backingStore.get(type, id, cacheFilter);
        if (item == null) {
            return null;
        }

        return mergeRelationships(item);
    }

    @Override
    public Collection<CacheData> getAll(String type) {
        return getAll(type, (CacheFilter) null);
    }

    @Override
    public Collection<CacheData> getAll(String type, CacheFilter cacheFilter) {
        validateTypes(type);
        Collection<CacheData> all = backingStore.getAll(type, cacheFilter);
        return buildResponse(all);
    }

    @Override
    public Collection<CacheData> getAll(String type, Collection<String> identifiers) {
        return getAll(type, identifiers, null);
    }

    @Override
    public Collection<CacheData> getAll(String type, Collection<String> identifiers, CacheFilter cacheFilter) {
        validateTypes(type);
        Collection<CacheData> byId = backingStore.getAll(type, identifiers, cacheFilter);
        return buildResponse(byId);
    }

    @Override
    public Collection<CacheData> getAll(String type, String... identifiers) {
        return getAll(type, Arrays.asList(identifiers));
    }

    @Override
    public Collection<String> existingIdentifiers(String type, Collection<String> identifiers) {
        Set<String> existing = new HashSet<>(backingStore.existingIdentifiers(type, identifiers));
        existing.remove(ALL_ID);
        return existing;
    }

    @Override
    public Collection<String> getIdentifiers(String type) {
        validateTypes(type);
        Set<String> identifiers = new HashSet<>(backingStore.getIdentifiers(type));
        identifiers.remove(ALL_ID);
        return identifiers;
    }

    @Override
    public Collection<String> filterIdentifiers(String type, String glob) {
        validateTypes(type);
        Set<String> identifiers = new HashSet<>(backingStore.filterIdentifiers(type, glob));
        identifiers.remove(ALL_ID);

        return identifiers;
    }

    @Override
    public void putCacheResult(String sourceAgentType, Collection<String> authoritativeTypes, CacheResult cacheResult) {
        Set<String> allTypes = new HashSet<>(cacheResult.getCacheResults().keySet());
        allTypes.addAll(authoritativeTypes);
        allTypes.addAll(cacheResult.getEvictions().keySet());
        validateTypes(allTypes);

        Map<String, Collection<String>> evictions = new HashMap<>();

        for (String type : allTypes) {
            final boolean authoritative = authoritativeTypes.contains(type);
            final Set<String> previousIdentifiers = authoritative ? getExistingSourceIdentifiers(type, sourceAgentType) : Collections.emptySet();
            final int originalSetSize = authoritative ? previousIdentifiers.size() : -1;
            int newItems = 0;
            final int cacheResultSize;
            if (cacheResult.getCacheResults().containsKey(type)) {
                final Collection<CacheData> cacheResultsForType = cacheResult.getCacheResults().get(type);
                cacheResultSize = cacheResultsForType.size();
                cacheDataType(type, sourceAgentType, cacheResultsForType);
                if (authoritative) {
                    for (CacheData data : cacheResultsForType) {
                        if (!previousIdentifiers.remove(data.getId())) {
                            newItems++;
                        }
                    }
                }
            } else {
              cacheResultSize = 0;
            }
            final Set<String> evictionsForType = new HashSet<>();
            final int explicitEvictionSize;
            if (cacheResult.getEvictions().containsKey(type)) {
                evictionsForType.addAll(cacheResult.getEvictions().get(type));
                explicitEvictionSize = evictions.size();
            } else {
                explicitEvictionSize = 0;
            }

            final int noLongerPresentItemsCount;
            if (authoritative) {
                noLongerPresentItemsCount = previousIdentifiers.size();
                evictionsForType.addAll(previousIdentifiers);
            } else {
                noLongerPresentItemsCount = -1;
            }

            if (!evictionsForType.isEmpty()) {
                evictions.put(type, evictionsForType);
            }

            final int totalEvictions = evictionsForType.size();
            final int changedItems = totalEvictions + newItems;
            final int changedPercentage = originalSetSize > 0 ? (int) Math.round(((double) changedItems / (double) originalSetSize) * 100d) : -1;
            final boolean highChangePercentage = changedPercentage > 5;
            final String authoritativeLabel = authoritative ? "authoritative" : "informative";

            Object[] params = {
                authoritativeLabel,
                sourceAgentType,
                type,
                originalSetSize,
                cacheResultSize,
                newItems,
                explicitEvictionSize,
                noLongerPresentItemsCount,
                totalEvictions,
                changedItems,
                changedPercentage
            };

            Map<String, String> metricTags = new HashMap<>();
            metricTags.put("authoritative", authoritativeLabel);
            metricTags.put("sourceAgentType", sourceAgentType);
            metricTags.put("type", type);
            metricTags.put("highChangePercentage", Boolean.toString(highChangePercentage));

            MetricBuilder mb = new MetricBuilder(registry, metricTags, "cats.defaultProviderCache.putCacheResult.");

            mb.counter("cacheResultSize").increment(cacheResultSize);
            mb.counter("newItems").increment(newItems);
            mb.counter("explicitEvictions").increment(explicitEvictionSize);
            if (authoritative) {
                mb.counter("noLongerPresentItems").increment(noLongerPresentItemsCount);
            }
            mb.counter("totalEvictions").increment(totalEvictions);
            mb.counter("changedItems").increment(changedItems);

            if (changedItems > 0) {
                if (highChangePercentage) {
                    mb.counter("highChangePercentage").increment();
                    log.warn("High changed percentage. " + DEBUG_METRICS, params);
                } else {
                    log.debug(DEBUG_METRICS, params);
                }
            }
        }

        for (Map.Entry<String, Collection<String>> eviction : evictions.entrySet()) {
          evictDeletedItems(eviction.getKey(), eviction.getValue());
        }
    }

    private static final String DEBUG_METRICS = "{} cache results for sourceAgentType {} type {}. " +
        "originalDataSetSize={}," +
        "cacheResultSize={}," +
        "newItems={}," +
        "explicitEvictions={}," +
        "noLongerPresentItems={}," +
        "totalEvictions={}," +
        "changedItems={}," +
        "changedPercentage={}";

    private static class MetricBuilder {
        private final Registry registry;
        private final Map<String, String> baseTags;
        private final String metricPrefix;

        public MetricBuilder(Registry registry, Map<String, String> baseTags, String metricPrefix) {
            this.registry = registry;
            this.baseTags = baseTags;
            this.metricPrefix = metricPrefix;
        }

        public Counter counter(String metricName) {
            return registry.counter(registry.createId(metricPrefix + metricName, baseTags));
        }
    }

    @Override
    public void putCacheData(String sourceAgentType, CacheData cacheData) {
        backingStore.merge(sourceAgentType, cacheData);
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

    private Collection<CacheData> buildResponse(Collection<CacheData> source) {
        Collection<CacheData> response = new ArrayList<>(source.size());
        for (CacheData item : source) {
            if (!ALL_ID.equals(item.getId())) {
                response.add(mergeRelationships(item));
            }
        }
        return Collections.unmodifiableCollection(response);
    }

    private Set<String> getExistingSourceIdentifiers(String type, String sourceAgentType) {
        CacheData all = backingStore.get(type, ALL_ID);
        if (all == null) {
            return new HashSet<>();
        }
        Collection<String> relationship = all.getRelationships().get(sourceAgentType);
        if (relationship == null) {
            return new HashSet<>();
        }
        if (relationship instanceof Set) {
          return (Set<String>) relationship;
        }
        return new HashSet<>(relationship);
    }

    private void cacheDataType(String type, String sourceAgentType, Collection<CacheData> items) {
        Collection<String> idSet = new HashSet<>();

        int ttlSeconds = -1;
        Collection<CacheData> toStore = new ArrayList<>(items.size() + 1);
        for (CacheData item : items) {
            idSet.add(item.getId());
            toStore.add(uniqueifyRelationships(item, sourceAgentType));

            if (item.getTtlSeconds() > ttlSeconds) {
                ttlSeconds = item.getTtlSeconds();
            }
        }
        Map<String, Collection<String>> allRelationship = new HashMap<>();
        allRelationship.put(sourceAgentType, idSet);

        toStore.add(new DefaultCacheData(ALL_ID, ttlSeconds, ALL_ATTRIBUTE, allRelationship));
        backingStore.mergeAll(type, toStore);
    }

    private CacheData uniqueifyRelationships(CacheData source, String sourceAgentType) {
        Map<String, Collection<String>> relationships = new HashMap<>(source.getRelationships().size());
        for (Map.Entry<String, Collection<String>> entry : source.getRelationships().entrySet()) {
            relationships.put(entry.getKey() + ':' + sourceAgentType, entry.getValue());
        }
        return new DefaultCacheData(source.getId(), source.getTtlSeconds(), source.getAttributes(), relationships);
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
