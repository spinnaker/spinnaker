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
import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
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
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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

    public interface CacheMetrics {
        void merge(String prefix, String type, int itemCount, int keysWritten, int relationshipCount, int hashMatches, int hashUpdates);
        class NOOP implements CacheMetrics {
            @Override
            public void merge(String prefix, String type, int itemCount, int keysWritten, int relationshipCount, int hashMatches, int hashUpdates) {
                //noop
            }
        }
    }

    private static final String DIGEST_ALGORITHM = "SHA1";
    private static final String HASH_CHARSET = "UTF8";

    private static final int DEFAULT_SCAN_SIZE = 50000;

    private static final TypeReference<Map<String, Object>> ATTRIBUTES = new TypeReference<Map<String, Object>>() {
    };
    private static final TypeReference<List<String>> RELATIONSHIPS = new TypeReference<List<String>>() {
    };

    private final String prefix;
    private final JedisSource source;
    private final ObjectMapper objectMapper;
    private final int maxMsetSize;
    private final CacheMetrics cacheMetrics;

    public RedisCache(String prefix, JedisSource source, ObjectMapper objectMapper, int maxMsetSize, CacheMetrics cacheMetrics) {
        Preconditions.checkArgument(
          maxMsetSize % 2 == 0, String.format("maxMsetSize must be even (%s)", maxMsetSize)
        );

        this.prefix = prefix;
        this.source = source;
        this.objectMapper = objectMapper.disable(SerializationFeature.WRITE_NULL_MAP_VALUES);
        this.maxMsetSize = maxMsetSize;
        this.cacheMetrics = cacheMetrics == null ? new CacheMetrics.NOOP() : cacheMetrics;
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
        int skippedWrites = 0;

        final MessageDigest digest;
        try {
            digest = MessageDigest.getInstance(DIGEST_ALGORITHM);
        } catch (NoSuchAlgorithmException nsae) {
            throw new RuntimeException(nsae);
        }

        final Map<String, byte[]> hashes = getHashes(type, items);
        final Map<byte[], byte[]> updatedHashes = new HashMap<>();

        for (CacheData item : items) {
            MergeOp op = buildMergeOp(type, item, hashes, digest);
            relationshipNames.addAll(op.relNames);
            keysToSet.addAll(op.keysToSet);
            idSet.add(item.getId());
            updatedHashes.putAll(op.hashesToSet);
            skippedWrites += op.skippedWrites;

            if (item.getTtlSeconds() > 0) {
                for (String key : op.keysToSet) {
                    ttlSecondsByKey.put(key, item.getTtlSeconds());
                }
            }
        }

        final String[] relationships = relationshipNames.toArray(new String[relationshipNames.size()]);
        final String[] ids = idSet.toArray(new String[idSet.size()]);

        if (keysToSet.size() > 0) {
            try (Jedis jedis = source.getJedis()) {
                Pipeline pipeline = jedis.pipelined();
                pipeline.sadd(allOfTypeReindex(type), ids);
                pipeline.sadd(allOfTypeId(type), ids);

                for (List<String> keys : Iterables.partition(keysToSet, maxMsetSize)) {
                    pipeline.mset(keys.toArray(new String[keys.size()]));
                }

                if (relationships.length > 0) {
                    pipeline.sadd(allRelationshipsId(type), relationships);
                }

                for (Map.Entry<String, Integer> ttlEntry : ttlSecondsByKey.entrySet()) {
                    pipeline.expire(ttlEntry.getKey(), ttlEntry.getValue());
                }

                if (!updatedHashes.isEmpty()) {
                    pipeline.hmset(hashesId(type), updatedHashes);
                }
                pipeline.sync();
            }
        }
        cacheMetrics.merge(prefix, type, items.size(), keysToSet.size() / 2, relationships.length, skippedWrites, updatedHashes.size());
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
        identifiers = new HashSet<>(identifiers);
        final String[] ids = identifiers.toArray(new String[identifiers.size()]);
        final Collection<String> allRelationships;
        try (Jedis jedis = source.getJedis()) {
            allRelationships = jedis.smembers(allRelationshipsId(type));
        }

        Collection<String> delKeys = new ArrayList<>((allRelationships.size() + 1) * ids.length);
        for (String id : ids) {
            for (String relationship : allRelationships) {
                delKeys.add(relationshipId(type, id, relationship));
            }
            delKeys.add(attributesId(type, id));
        }

        try (Jedis jedis = source.getJedis()) {
            Pipeline pipe = jedis.pipelined();
            pipe.del(delKeys.toArray(new String[delKeys.size()]));
            pipe.srem(allOfTypeId(type), ids);
            pipe.srem(allOfTypeReindex(type), ids);
            pipe.hdel(hashesId(type), stringsToBytes(delKeys));
            pipe.sync();
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

    private byte[] stringToBytes(String string) {
        return string.getBytes(Charset.forName(HASH_CHARSET));
    }

    private byte[][] stringsToBytes(Collection<String> strings) {
        final byte[][] results = new byte[strings.size()][];
        int i = 0;
        for (String string : strings) {
          results[i++] = stringToBytes(string);
        }
        return results;
    }

    private static class MergeOp {
        final Set<String> relNames;
        final List<String> keysToSet;
        final Map<byte[], byte[]> hashesToSet;
        final int skippedWrites;

        public MergeOp(Set<String> relNames, List<String> keysToSet, Map<byte[], byte[]> hashesToSet, int skippedWrites) {
            this.relNames = relNames;
            this.keysToSet = keysToSet;
            this.hashesToSet = hashesToSet;
            this.skippedWrites = skippedWrites;
        }
    }

    /**
     * Compares the hash of serializedValue against an existing hash, if they do not match adds
     * serializedValue to keys and the new hash to updatedHashes.
     * @param hashes the existing hash values
     * @param id the id of the item
     * @param serializedValue the serialized value
     * @param digest the digest for hash computation
     * @param keys values to persist - if the hash does not match id and serializedValue are appended
     * @param updatedHashes hashes to persist - if the hash does not match adds an entry of id -> computed hash
     * @return true if the hash matched, false otherwise
     */
    private boolean hashCheck(Map<String, byte[]> hashes, String id, String serializedValue, MessageDigest digest, List<String> keys, Map<byte[], byte[]> updatedHashes) {
        final byte[] hash = digest.digest(stringToBytes(serializedValue));
        final byte[] existingHash = hashes.get(id);
        if (Arrays.equals(hash, existingHash)) {
           return true;
        }

        keys.add(id);
        keys.add(serializedValue);
        updatedHashes.put(stringToBytes(id), hash);
        return false;
    }

    private MergeOp buildMergeOp(String type, CacheData cacheData, Map<String, byte[]> hashes, MessageDigest digest) {
        int skippedWrites = 0;
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

        final Map<byte[], byte[]> hashesToSet = new HashMap<>();
        final List<String> keysToSet = new ArrayList<>((cacheData.getRelationships().size() + 1) * 2);
        if (serializedAttributes != null &&
            hashCheck(hashes, attributesId(type, cacheData.getId()), serializedAttributes, digest, keysToSet, hashesToSet)) {
            skippedWrites++;
        }

        if (!cacheData.getRelationships().isEmpty()) {
            for (Map.Entry<String, Collection<String>> relationship : cacheData.getRelationships().entrySet()) {
                final String relationshipValue;
                try {
                    relationshipValue = objectMapper.writeValueAsString(new LinkedHashSet<>(relationship.getValue()));
                } catch (JsonProcessingException serializationException) {
                    throw new RuntimeException("Relationship serialization failed", serializationException);
                }
                if (hashCheck(hashes, relationshipId(type, cacheData.getId(), relationship.getKey()), relationshipValue, digest, keysToSet, hashesToSet)) {
                    skippedWrites++;
                }
            }
        }

        return new MergeOp(cacheData.getRelationships().keySet(), keysToSet, hashesToSet, skippedWrites);
    }

    private List<String> getKeys(String type, Collection<CacheData> cacheDatas) {
        final Collection<String> keys = new HashSet<>();
        for (CacheData cacheData : cacheDatas) {
            if (!cacheData.getAttributes().isEmpty()) {
                keys.add(attributesId(type, cacheData.getId()));
            }
            if (!cacheData.getRelationships().isEmpty()) {
                for (String relationship : cacheData.getRelationships().keySet()) {
                    keys.add(relationshipId(type, cacheData.getId(), relationship));
                }
            }
        }
        return new ArrayList<>(keys);
    }

    private boolean isHashingDisabled(String type) {
        try (Jedis jedis = source.getJedis()) {
            return jedis.exists(hashesDisabled(type));
        }
    }

    private Map<String, byte[]> getHashes(String type, Collection<CacheData> items) {
        if (isHashingDisabled(type)) {
            return Collections.emptyMap();
        }

        final List<String> hashKeys = getKeys(type, items);
        if (hashKeys.isEmpty()) {
            return Collections.emptyMap();
        }

        final List<byte[]> hashValues;
        final byte[] hashesId = hashesId(type);

        try (Jedis jedis = source.getJedis()) {
            hashValues = jedis.hmget(hashesId, stringsToBytes(hashKeys));
        }
        if (hashValues.size() != hashKeys.size()) {
            throw new RuntimeException("Expected same size result as request");
        }
        final Map<String, byte[]> hashes = new HashMap<>(hashKeys.size());
        for (int i = 0; i < hashValues.size(); i++) {
            final byte[] hashValue = hashValues.get(i);
            if (hashValue != null) {
                hashes.put(hashKeys.get(i), hashValue);
            }
        }

        return isHashingDisabled(type) ? Collections.emptyMap() : hashes;
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

    private byte[] hashesId(String type) {
        return stringToBytes(String.format("%s:%s:hashes", prefix, type));
    }

    private String hashesDisabled(String type) {
        return String.format("%s:%s:hashes.disabled", prefix, type);
    }

    private String allRelationshipsId(String type) {
        return String.format("%s:%s:relationships", prefix, type);
    }

    private String allOfTypeId(String type) {
        return String.format("%s:%s:members", prefix, type);
    }

    private String allOfTypeReindex(String type) {
        return String.format("%s:%s:members.2", prefix, type);
    }
}
