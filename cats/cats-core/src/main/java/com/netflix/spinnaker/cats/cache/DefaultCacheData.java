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
import java.util.Collections;
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

    public DefaultCacheData(String id, int ttlSeconds, Map<String, Object> attributes, Map<String, Collection<String>> relationships) {
        this.id = id;
        this.ttlSeconds = ttlSeconds;
        this.attributes = attributes;
        this.relationships = relationships;
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
