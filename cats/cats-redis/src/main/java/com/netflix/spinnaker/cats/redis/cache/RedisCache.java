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

package com.netflix.spinnaker.cats.redis.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.CacheFilter;
import com.netflix.spinnaker.cats.cache.DefaultCacheData;
import com.netflix.spinnaker.cats.cache.WriteableCache;
import com.netflix.spinnaker.cats.redis.JedisSource;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.Pipeline;
import redis.clients.jedis.ScanParams;
import redis.clients.jedis.ScanResult;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class RedisCache implements WriteableCache {

    private static final int DEFAULT_SCAN_SIZE = 50000;

    private static final TypeReference<Map<String, Object>> ATTRIBUTES = new TypeReference<Map<String, Object>>() {
    };
    private static final TypeReference<List<String>> RELATIONSHIPS = new TypeReference<List<String>>() {
    };

    private final String prefix;
    private final JedisSource source;
    private final ObjectMapper objectMapper;

    public RedisCache(String prefix, JedisSource source, ObjectMapper objectMapper) {
        this.prefix = prefix;
        this.source = source;
        this.objectMapper = objectMapper.disable(SerializationFeature.WRITE_NULL_MAP_VALUES);
    }

    @Override
    public void mergeAll(String type, Collection<CacheData> items) {
        if (items.isEmpty()) {
            return;
        }
        final Set<String> relationshipNames = new HashSet<>();
        final List<String> keysToSet = new LinkedList<>();
        final Set<String> idSet = new HashSet<>();

        final Map<String, Integer> ttlSecondsByKey = new HashMap<>();

        for (CacheData item : items) {
            MergeOp op = buildMergeOp(type, item);
            relationshipNames.addAll(op.relNames);
            keysToSet.addAll(op.keysToSet);
            idSet.add(item.getId());

            if (item.getTtlSeconds() > 0) {
                for (String key : op.keysToSet) {
                    ttlSecondsByKey.put(key, item.getTtlSeconds());
                }
            }
        }

        final String[] relationships = relationshipNames.toArray(new String[relationshipNames.size()]);
        final String[] ids = idSet.toArray(new String[idSet.size()]);
        final String[] mset = keysToSet.toArray(new String[keysToSet.size()]);

        if (mset.length > 0) {
            try (Jedis jedis = source.getJedis()) {
                Pipeline pipeline = jedis.pipelined();
                pipeline.sadd(allOfTypeId(type), ids);
                pipeline.mset(mset);
                if (relationships.length > 0) {
                    pipeline.sadd(allRelationshipsId(type), relationships);
                }

                for (Map.Entry<String, Integer> ttlEntry : ttlSecondsByKey.entrySet()) {
                    pipeline.expire(ttlEntry.getKey(), ttlEntry.getValue());
                }
                pipeline.sync();
            }
        }
    }

    @Override
    public void merge(String type, CacheData item) {
        mergeAll(type, Arrays.asList(item));
    }

    @Override
    public void evictAll(String type, Collection<String> identifiers) {
        if (identifiers.isEmpty()) {
            return;
        }
        Collection<String> ids = new HashSet<>(identifiers);
        final Collection<String> allRelationships;
        try (Jedis jedis = source.getJedis()) {
            allRelationships = jedis.smembers(allRelationshipsId(type));
        }

        Collection<String> delKeys = new ArrayList<>((allRelationships.size() + 1) * ids.size());
        for (String id : ids) {
            for (String relationship : allRelationships) {
                delKeys.add(relationshipId(type, id, relationship));
            }
            delKeys.add(attributesId(type, id));
        }

        try (Jedis jedis = source.getJedis()) {
            jedis.del(delKeys.toArray(new String[delKeys.size()]));
            jedis.srem(allOfTypeId(type), ids.toArray(new String[ids.size()]));
        }
    }

    @Override
    public void evict(String type, String id) {
        evictAll(type, Arrays.asList(id));
    }

    @Override
    public CacheData get(String type, String id) {
        return get(type, id, null);
    }

    @Override
    public CacheData get(String type, String id, CacheFilter cacheFilter) {
        Collection<CacheData> result = getAll(type, Arrays.asList(id), cacheFilter);
        if (result.isEmpty()) {
            return null;
        }
        return result.iterator().next();
    }

    public Collection<CacheData> getAll(String type,
                                        Collection<String> identifiers,
                                        CacheFilter cacheFilter) {
        if (identifiers.isEmpty()) {
            return Collections.emptySet();
        }
        Collection<String> ids = new LinkedHashSet<>(identifiers);
        final List<String> knownRels;
        try (Jedis jedis = source.getJedis()) {
            Set<String> allRelationships = jedis.smembers(allRelationshipsId(type));
            if (cacheFilter == null) {
                knownRels = new ArrayList<>(allRelationships);
            } else {
                knownRels = new ArrayList<>(cacheFilter.filter(CacheFilter.Type.RELATIONSHIP, allRelationships));
            }
        }

        final int singleResultSize = knownRels.size() + 1;

        final List<String> keysToGet = new ArrayList<>(singleResultSize * ids.size());
        for (String id : ids) {
            keysToGet.add(attributesId(type, id));
            for (String rel : knownRels) {
                keysToGet.add(relationshipId(type, id, rel));
            }
        }

        final String[] mget = keysToGet.toArray(new String[keysToGet.size()]);

        final List<String> keyResult;

        try (Jedis jedis = source.getJedis()) {
            keyResult = jedis.mget(mget);
        }

        if (keyResult.size() != mget.length) {
            throw new RuntimeException("Expected same size result as request");
        }

        Collection<CacheData> results = new ArrayList<>(ids.size());
        Iterator<String> idIterator = identifiers.iterator();
        for (int ofs = 0; ofs < keyResult.size(); ofs += singleResultSize) {
            CacheData item = extractItem(idIterator.next(), keyResult.subList(ofs, ofs + singleResultSize), knownRels);
            if (item != null) {
                results.add(item);
            }
        }
        return results;
    }

    @Override
    public Collection<CacheData> getAll(String type, Collection<String> identifiers) {
        return getAll(type, identifiers, null);
    }

    @Override
    public Collection<CacheData> getAll(String type) {
        return getAll(type, (CacheFilter) null);
    }

    @Override
    public Collection<CacheData> getAll(String type, CacheFilter cacheFilter) {
        final Set<String> allIds;
        try (Jedis jedis = source.getJedis()) {
            allIds = jedis.smembers(allOfTypeId(type));
        }
        return getAll(type, allIds, cacheFilter);
    }

    @Override
    public Collection<CacheData> getAll(String type, String... identifiers) {
        return getAll(type, Arrays.asList(identifiers));
    }

    @Override
    public Collection<String> getIdentifiers(String type) {
        try (Jedis jedis = source.getJedis()) {
            return jedis.smembers(allOfTypeId(type));
        }
    }

    @Override
    public Collection<String> filterIdentifiers(String type, String glob) {
        try (Jedis jedis = source.getJedis()) {
            final Set<String> matches = new HashSet<>();
            final ScanParams scanParams = new ScanParams().match(glob).count(DEFAULT_SCAN_SIZE);
            final String allIdentifiersKey = allOfTypeId(type);
            String cursor = "0";
            while (true) {
                final ScanResult<String> scanResult = jedis.sscan(allIdentifiersKey, cursor, scanParams);
                matches.addAll(scanResult.getResult());
                cursor = scanResult.getStringCursor();
                if ("0".equals(cursor)) {
                    return matches;
                }
            }
        }
    }

    private static class MergeOp {
        final Set<String> relNames;
        final List<String> keysToSet;

        public MergeOp(Set<String> relNames, List<String> keysToSet) {
            this.relNames = relNames;
            this.keysToSet = keysToSet;
        }
    }

    private MergeOp buildMergeOp(String type, CacheData cacheData) {
        final String serializedAttributes;
        try {
            if (cacheData.getAttributes().isEmpty()) {
                serializedAttributes = null;
            } else {
                serializedAttributes = objectMapper.writeValueAsString(cacheData.getAttributes());
            }
        } catch (JsonProcessingException serializationException) {
            throw new RuntimeException("Attribute serialization failed", serializationException);
        }

        final List<String> keysToSet = new ArrayList<>((cacheData.getRelationships().size() + 1) * 2);
        if (serializedAttributes != null) {
            keysToSet.add(attributesId(type, cacheData.getId()));
            keysToSet.add(serializedAttributes);
        }

        if (!cacheData.getRelationships().isEmpty()) {
            for (Map.Entry<String, Collection<String>> relationship : cacheData.getRelationships().entrySet()) {
                keysToSet.add(relationshipId(type, cacheData.getId(), relationship.getKey()));
                try {
                    keysToSet.add(objectMapper.writeValueAsString(new LinkedHashSet<>(relationship.getValue())));
                } catch (JsonProcessingException serializationException) {
                    throw new RuntimeException("Relationship serialization failed", serializationException);
                }
            }
        }

        return new MergeOp(cacheData.getRelationships().keySet(), keysToSet);
    }

    private CacheData extractItem(String id, List<String> keyResult, List<String> knownRels) {
        if (keyResult.get(0) == null) {
            return null;
        }

        try {
            final Map<String, Object> attributes = objectMapper.readValue(keyResult.get(0), ATTRIBUTES);
            final Map<String, Collection<String>> relationships = new HashMap<>(keyResult.size() - 1);
            for (int relIdx = 1; relIdx < keyResult.size(); relIdx++) {
                String rel = keyResult.get(relIdx);
                if (rel != null) {
                    String relType = knownRels.get(relIdx - 1);
                    Collection<String> deserializedRel = objectMapper.readValue(rel, RELATIONSHIPS);
                    relationships.put(relType, deserializedRel);
                }
            }

            return new DefaultCacheData(id, attributes, relationships);

        } catch (IOException deserializationException) {
            throw new RuntimeException("Deserialization failed", deserializationException);
        }
    }

    private String attributesId(String type, String id) {
        return String.format("%s:%s:attributes:%s", prefix, type, id);
    }

    private String relationshipId(String type, String id, String relationship) {
        return String.format("%s:%s:relationships:%s:%s", prefix, type, id, relationship);
    }

    private String allRelationshipsId(String type) {
        return String.format("%s:%s:relationships", prefix, type);
    }

    private String allOfTypeId(String type) {
        return String.format("%s:%s:members", prefix, type);
    }
}
