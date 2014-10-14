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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.DefaultCacheData;
import com.netflix.spinnaker.cats.cache.WriteableCache;
import com.netflix.spinnaker.cats.redis.JedisSource;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.Transaction;

import java.io.IOException;
import java.util.*;

//TODO-CF there is an opportunity to optimize the *All methods for now they just iterate and delegate to the
// single method
public class RedisCache implements WriteableCache {

    private final String prefix;
    private final JedisSource source;
    private final ObjectMapper objectMapper;


    public RedisCache(String prefix, JedisSource source, ObjectMapper objectMapper) {
        this.prefix = prefix;
        this.source = source;
        this.objectMapper = objectMapper;
    }

    @Override
    public void merge(String type, CacheData cacheData) {
        final Map<String, String> hash = new HashMap<>(cacheData.getAttributes().size());
        final Collection<String> hashDeletions = new ArrayList<>();

        for (Map.Entry<String, Object> attribute : cacheData.getAttributes().entrySet()) {
            if (attribute.getValue() == null) {
                hashDeletions.add(attribute.getKey());
            } else {
                try {
                    hash.put(attribute.getKey(), objectMapper.writeValueAsString(attribute.getValue()));
                } catch (JsonProcessingException jpe) {
                    throw new RuntimeException("Attribute serialization failed", jpe);
                }
            }
        }
        hash.put(ID_ATTRIBUTE, cacheData.getId());

        try (Jedis jedis = source.getJedis()) {
            jedis.sadd(allOfTypeId(type), cacheData.getId());
            if (!hashDeletions.isEmpty()) {
                jedis.hdel(attributesId(type, cacheData.getId()), hashDeletions.toArray(new String[hashDeletions.size()]));
            }
            jedis.hmset(attributesId(type, cacheData.getId()), hash);
            if (!cacheData.getRelationships().isEmpty()) {
                jedis.sadd(allRelationshipsId(type, cacheData.getId()), cacheData.getRelationships().keySet().toArray(new String[cacheData.getRelationships().size()]));
                for (Map.Entry<String, Collection<String>> relationship : cacheData.getRelationships().entrySet()) {
                    Transaction xa = jedis.multi();
                    xa.del(relationshipId(type, cacheData.getId(), relationship.getKey()));
                    if (!relationship.getValue().isEmpty()) {
                        xa.sadd(relationshipId(type, cacheData.getId(), relationship.getKey()), relationship.getValue().toArray(new String[relationship.getValue().size()]));
                    }
                    xa.exec();
                }
            }
        }
    }

    @Override
    public void mergeAll(String type, Collection<CacheData> items) {
        for (CacheData item : items) {
            merge(type, item);
        }
    }

    @Override
    public void evict(String type, String id) {
        try (Jedis jedis = source.getJedis()) {
            Collection<String> allRelationships = jedis.smembers(allRelationshipsId(type, id));
            Collection<String> delKeys = new ArrayList<>(allRelationships.size() + 2);
            for (String relationship : allRelationships) {
                delKeys.add(relationshipId(type, id, relationship));
            }
            delKeys.add(attributesId(type, id));
            delKeys.add(allRelationshipsId(type, id));
            jedis.del(delKeys.toArray(new String[delKeys.size()]));
            jedis.srem(allOfTypeId(type), id);
        }
    }

    @Override
    public void evictAll(String type, Collection<String> ids) {
        for (String id : ids) {
            evict(type, id);
        }
    }

    @Override
    public CacheData get(String type, String id) {
        final Map<String, String> hash;
        final Map<String, Collection<String>> relationships;
        try (Jedis jedis = source.getJedis()) {
            hash = jedis.hgetAll(attributesId(type, id));
            if (hash.isEmpty()) {
                relationships = Collections.emptyMap();
            } else {
                Collection<String> rels = jedis.smembers(allRelationshipsId(type, id));
                relationships = new HashMap<>(rels.size());
                for (String relationshipName : rels) {
                    Collection<String> relationship = jedis.smembers(relationshipId(type, id, relationshipName));
                    relationships.put(relationshipName, relationship);
                }
            }
        }
        if (hash.isEmpty()) {
            return null;
        }

        Map<String, Object> attributes = new HashMap<>(hash.size());
        for (Map.Entry<String, String> serialized : hash.entrySet()) {
            if (!ID_ATTRIBUTE.equals(serialized.getKey())) {
                try {
                    attributes.put(serialized.getKey(), objectMapper.readValue(serialized.getValue(), Object.class));
                } catch (IOException ex) {
                    throw new RuntimeException("Attribute deserialization failed", ex);
                }
            }
        }
        return new DefaultCacheData(id, attributes, relationships);
    }

    @Override
    public Collection<CacheData> getAll(String type) {
        final Set<String> allIds;
        try (Jedis jedis = source.getJedis()) {
            allIds = jedis.smembers(allOfTypeId(type));
        }
        Collection<CacheData> results = new ArrayList<>(allIds.size());
        for (String id : allIds) {
            CacheData result = get(type, id);
            if (result != null) {
                results.add(result);
            }
        }
        return results;
    }

    @Override
    public Collection<String> getIdentifiers(String type) {
        try (Jedis jedis = source.getJedis()) {
            return jedis.smembers(allOfTypeId(type));
        }
    }

    private String attributesId(String type, String id) {
        return String.format("%s:%s:attributes:%s", prefix, type, id);
    }

    private String relationshipId(String type, String id, String relationship) {
        return String.format("%s:%s:relationships:%s:%s", prefix, type, id, relationship);
    }

    private String allRelationshipsId(String type, String id) {
        return String.format("%s:%s:relationships:%s", prefix, type, id);
    }

    private String allOfTypeId(String type) {
        return String.format("%s:%s:members", prefix, type);
    }

    private static final String ID_ATTRIBUTE = "__ID__";
}
