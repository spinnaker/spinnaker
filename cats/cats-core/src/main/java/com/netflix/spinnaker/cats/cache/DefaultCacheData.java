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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Clock;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * An immutable CacheData.
 */
public class DefaultCacheData implements CacheData {
    private final String id;
    private final int ttlSeconds;
    private final Map<String, Object> attributes;
    private final Map<String, Collection<String>> relationships;

    public DefaultCacheData(String id, Map<String, Object> attributes, Map<String, Collection<String>> relationships) {
        this(id, -1, attributes, relationships);
    }

    @JsonCreator
    public DefaultCacheData(@JsonProperty("id") String id,
                            @JsonProperty("ttlSeconds") int ttlSeconds,
                            @JsonProperty("attributes") Map<String, Object> attributes,
                            @JsonProperty("relationships") Map<String, Collection<String>> relationships) {
        this(id, ttlSeconds, attributes, relationships, Clock.systemDefaultZone());
    }

    public DefaultCacheData(String id, int ttlSeconds, Map<String, Object> attributes, Map<String, Collection<String>> relationships, Clock clock) {
        // ensure attributes is non-null and mutable given that `cacheExpiry` will be added
        attributes = attributes == null ? new HashMap<>() : new HashMap<>(attributes);

        this.id = id;
        this.attributes = attributes;
        this.relationships = relationships;

        if (ttlSeconds > 0) {
            Long cacheExpiry = clock.millis() + ttlSeconds * 1000;
            this.attributes.put("cacheExpiry", cacheExpiry);
        }

        if (ttlSeconds < 0 && attributes.containsKey("cacheExpiry")) {
            ttlSeconds = (int) (clock.millis() - (long) attributes.get("cacheExpiry")) * -1 / 1000;
        }

        this.ttlSeconds = ttlSeconds;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public int getTtlSeconds() {
        return ttlSeconds;
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
