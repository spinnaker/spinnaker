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



package com.netflix.spinnaker.mort.model

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

class InMemoryCacheService implements CacheService {
  private final ConcurrentMap<String, ConcurrentMap<String, Object>> caches = new ConcurrentHashMap<>()

  @Override
  public <T> T retrieve(String key, Class<T> type) {
    getCache(key)?.get key
  }

  @Override
  boolean put(String key, Object object) {
    ConcurrentMap<String, Object> typeMap = new ConcurrentHashMap<>()
    ConcurrentMap<String, Object> existing = caches.putIfAbsent(getCacheType(key), typeMap)
    def v = ((existing != null)? existing: typeMap).put key, object
    v != null
  }

  @Override
  void free(String key) {
    getCache(key)?.remove(key)
  }

  @Override
  boolean exists(String key) {
    getCache(key)?.containsKey key
  }

  @Override
  Set<String> keys() {
    caches.values().collect { it.keySet() }.flatten()
  }

  @Override
  Set<String> keysByType(String type) {
    caches.get(type)?.keySet() ?: []
  }

  Set<String> keysByType(Object type) {
    keysByType(type as String)
  }

  String getCacheType(String key) {
    int typeIndex = key.indexOf(':')
    if (typeIndex == -1) {
      throw new IllegalStateException("Expected type prefix in key ${key}")
    }
    key.substring(0, typeIndex)
  }

  ConcurrentMap<String, Object> getCache(String key) {
    caches.get(getCacheType(key))
  }
}
