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

package com.netflix.spinnaker.cats.test

import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.provider.Provider
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.cats.provider.ProviderRegistry

class TestProviderRegistry implements ProviderRegistry {
    Provider provider
    ProviderCache cache

    TestProviderRegistry(Provider provider, ProviderCache cache) {
        this.provider = provider
        this.cache = cache
    }

    @Override
    Collection<Provider> getProviders() {
        [provider]
    }

    @Override
    Collection<Cache> getProviderCaches() {
        [cache]
    }

    @Override
    ProviderCache getProviderCache(String providerName) {
        cache
    }
}
