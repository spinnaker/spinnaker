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

package com.netflix.spinnaker.cats.cache

import com.netflix.spinnaker.cats.mem.InMemoryCache

class CompositeCacheSpec extends CacheSpec {

    WriteableCache c1
    WriteableCache c2

    @Override
    Cache getSubject() {
        c1 = new InMemoryCache()
        c2 = new InMemoryCache()
        new CompositeCache(Arrays.asList(c1, c2))
    }

    @Override
    void populateOne(String type, String id, CacheData cacheData = new DefaultCacheData(id, [id: id], [:])) {
        c1.merge(type, cacheData)
    }

    def "attributes are merged from both caches"() {
        setup:
        c1.merge('foo', createData('bar', [c1Att: 'c1washere']))
        c2.merge('foo', createData('bar', [c2Att: 'c2washere']))

        when:
        def bar = cache.get('foo', 'bar')

        then:
        bar.attributes.c1Att == 'c1washere'
        bar.attributes.c2Att == 'c2washere'
    }
}
