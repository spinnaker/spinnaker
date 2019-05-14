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

import com.netflix.spinnaker.cats.cache.NamedCacheFactory;
import com.netflix.spinnaker.cats.cache.WriteableCache;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Produces InMemoryCaches. */
public class InMemoryNamedCacheFactory implements NamedCacheFactory {
  private final ConcurrentMap<String, WriteableCache> caches = new ConcurrentHashMap<>();

  @Override
  public WriteableCache getCache(String name) {
    WriteableCache cache = new InMemoryCache();
    WriteableCache existing = caches.putIfAbsent(name, cache);
    if (existing == null) {
      return cache;
    }
    return existing;
  }
}
