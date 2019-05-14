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

import com.netflix.spinnaker.cats.cache.Cache;
import com.netflix.spinnaker.cats.cache.NamedCacheFactory;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class DefaultProviderRegistry implements ProviderRegistry {
  private final ConcurrentMap<String, ProviderCache> providerCaches = new ConcurrentHashMap<>();
  private final Collection<Provider> providers;

  public DefaultProviderRegistry(Collection<Provider> providers, NamedCacheFactory cacheFactory) {
    this.providers = Collections.unmodifiableCollection(providers);
    for (Provider provider : providers) {
      providerCaches.put(
          provider.getProviderName(),
          new DefaultProviderCache(cacheFactory.getCache(provider.getProviderName())));
    }
  }

  @Override
  public Collection<Provider> getProviders() {
    return providers;
  }

  @Override
  public Collection<Cache> getProviderCaches() {
    return Collections.<Cache>unmodifiableCollection(providerCaches.values());
  }

  @Override
  public ProviderCache getProviderCache(String providerName) {
    return providerCaches.get(providerName);
  }
}
