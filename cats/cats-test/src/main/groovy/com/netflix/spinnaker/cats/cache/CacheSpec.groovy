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
import spock.lang.Unroll

abstract class CacheSpec extends Specification {

    @Subject
    Cache cache

    def setup() {
        cache = getSubject()
    }

    abstract Cache getSubject()

    void populateOne(String type, String id, CacheData data = createData(id)) {
        ((WriteableCache) cache).merge(type, data)
    }

    CacheData createData(String id, Map attributes = [id: id], Map relationships = [:]) {
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

    def 'identifiers behaviour'() {
        setup:
        populateOne('foo', 'bar')
        populateOne('foo', 'baz')

        expect:
        cache.getIdentifiers('foo').sort() == ['bar', 'baz']
    }

    def 'filterIdentifiers behaviour'() {
        setup:
        for (String id : identifiers) {
            populateOne('foo', id)
        }

        expect:
        cache.filterIdentifiers('foo', filter).toSet() == expected as Set

        where:
        filter                  | expected
        '*TEST*'                | ['blaTEST', 'blaTESTbla', 'TESTbla']
        'bla*'                  | ['blaTEST', 'blaTESTbla', 'blaPest', 'blaFEST']
        'bla[TF]EST'            | ['blaTEST', 'blaFEST']
        'bla?EST'               | ['blaTEST', 'blaFEST']
        '??a[FTP][Ee][Ss][Tt]*' | ['blaTEST', 'blaTESTbla', 'blaPest', 'blaFEST']

        identifiers = ['blaTEST', 'TESTbla', 'blaTESTbla', 'blaPest', 'blaFEST']
    }

    def 'can getAll empty id collection'() {
        when:
        def results = cache.getAll('foo', [])

        then:
        results != null
        results.isEmpty()
    }

    def 'get by id behaviour'() {
        setup:
        populateOne('foo', 'bar')
        populateOne('foo', 'baz')

        when:
        def results = cache.getAll('foo', 'bar', 'baz', 'doesntexist')

        then:
        results != null
        results.size() == 2
        results.find { it.id == 'bar' }
        results.find { it.id == 'baz' }
    }

    @Unroll
    def 'relationship filtering behaviour'() {
        setup:
        populateOne('foo', 'bar', createData('bar', [bar: "bar"], [rel1: ["rel1"], rel2: ["rel2"]]))

        expect:
        cache.get('foo', 'bar').relationships.keySet() == ["rel1", "rel2"] as Set
        cache.get('foo', 'bar', filter).relationships.keySet() == expectedRelationships as Set

        cache.getAll('foo').iterator().next().relationships.keySet() == ["rel1", "rel2"] as Set
        cache.getAll('foo', filter).iterator().next().relationships.keySet() == expectedRelationships as Set

        where:
        filter                                          || expectedRelationships
        RelationshipCacheFilter.include("rel1")         || ["rel1"]
        RelationshipCacheFilter.include("rel1", "rel2") || ["rel1", "rel2"]
        RelationshipCacheFilter.include("rel3")         || []
        RelationshipCacheFilter.none()                  || []
    }
}
