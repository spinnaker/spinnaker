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

import spock.lang.Specification
import spock.lang.Subject

abstract class CacheSpec extends Specification {

    @Subject Cache cache

    def setup() {
        cache = getSubject()
    }

    abstract Cache getSubject()

    void populateOne(String type, String id) {
        ((WriteableCache) cache).merge(type, createData(id))
    }

    CacheData createData(String id, Map attributes = [:], Map relationships = [:]) {
        new DefaultCacheData(id, attributes, relationships)
    }

    def 'empty cache behaviour'() {
        expect:
        cache.get('foo', 'bar') == null
        cache.getAll('foo') != null
        cache.getAll('foo').isEmpty()
    }

    def 'existing value behaviour'() {
        setup:
        populateOne('foo', 'bar')

        expect:
        cache.get('foo', 'bar') != null
        cache.getAll('foo').size() == 1
        cache.getAll('foo').first().id == 'bar'
    }
}
