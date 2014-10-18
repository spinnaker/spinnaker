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

abstract class WriteableCacheSpec extends CacheSpec {

    WriteableCache getCache() {
        super.cache as WriteableCache
    }

    def 'merge creates a new value'() {
        when:
        cache.merge('foo', createData('bar'))

        then:
        cache.get('foo', 'bar') != null
    }

    def 'merge stores relationships'() {
        when:
        cache.merge('fooWithRels', createData('bar', [mergeAtt: 'merged'], [rel1: ['abc', 'def'], rel2: ['ghi', 'jkl']]))
        def retrieved = cache.get('fooWithRels', 'bar')

        then:
        retrieved != null
        retrieved.id == 'bar'
        retrieved.attributes.mergeAtt == 'merged'
        retrieved.relationships.size() == 2
        retrieved.relationships.rel1.size() == 2
        retrieved.relationships.rel1.containsAll(['abc', 'def'])
        retrieved.relationships.rel2.size() == 2
        retrieved.relationships.rel2.containsAll(['ghi', 'jkl'])
    }

    def 'merge replaces all attributes'() {
        setup:
        cache.merge('foo', createData('bar', [merge1: 'merge1']))

        when:
        def bar = cache.get('foo', 'bar')

        then:
        bar != null
        bar.attributes.size() == 1
        bar.attributes.merge1 == 'merge1'

        when:
        cache.merge('foo', createData('bar', [merge2: 'merge2']))
        bar = cache.get('foo', 'bar')

        then:
        bar != null
        bar.attributes.size() == 1
        bar.attributes.merge1 == null
        bar.attributes.merge2 == 'merge2'
    }

    def 'can evictAll empty collection'() {
        when:
        cache.evictAll('foo', [])

        then:
        noExceptionThrown()
    }

    def 'can mergeAll empty collection'() {
        when:
        cache.mergeAll('foo', [])

        then:
        noExceptionThrown()
    }

    def 'merging null attributes removes the value'() {
        setup:
        cache.merge('foo', createData('bar', [merge1: 'merge1']))

        when:
        def bar = cache.get('foo', 'bar')

        then:
        bar != null
        bar.attributes.size() == 1
        bar.attributes.merge1 == 'merge1'

        when:
        cache.merge('foo', createData('bar', [merge1: null, merge2: 'merge2']))
        bar = cache.get('foo', 'bar')

        then:
        bar != null
        bar.attributes.size() == 1
        bar.attributes.merge2 == 'merge2'
    }

    def 'mergeAll with empty collection'() {
        when:
        cache.mergeAll('foo', [])

        then:
        noExceptionThrown()

    }

    def 'mergeAll merges all items'() {
        when:
        cache.mergeAll('foo', [createData('bar', [att1: 'val1']), createData('baz', [att2: 'val2']), createData('bar2', [bar2: 'bar2'])])
        def bar = cache.get('foo', 'bar')
        def baz = cache.get('foo', 'baz')
        def bar2 = cache.get('foo', 'bar2')
        def allFoo = cache.getAll('foo')

        then:
        bar != null
        bar.id == 'bar'
        bar.attributes.size() == 1
        bar.attributes.att1 == 'val1'
        baz != null
        baz.id == 'baz'
        baz.attributes.att2 == 'val2'
        bar2 != null
        bar2.id == 'bar2'
        bar2.attributes.size() == 1
        bar2.attributes.bar2 == 'bar2'
        allFoo != null
        allFoo.size() == 3
    }

    def 'evict removes the item'() {
        setup:
        cache.merge('foo', createData('bar'))

        when:
        def bar = cache.get('foo', 'bar')

        then:
        bar != null

        when:
        cache.evict('foo', 'bar')
        bar = cache.get('foo', 'bar')

        then:
        bar == null
    }

    def 'evict ignores non-existing items'() {
        when:
        def neverWasThere = cache.get('nothere', 'notatall')

        then:
        neverWasThere == null

        when:
        cache.evict('nothere', 'notatall')

        then:
        noExceptionThrown()

        when:
        neverWasThere = cache.get('nothere', 'notatall')

        then:
        neverWasThere == null
    }

    def 'evictAll removes all present items'() {
        setup:
        5.times {
            cache.merge('foo', createData('bar' + it))
        }

        when:
        def allFoo = cache.getAll('foo')

        then:
        allFoo != null
        allFoo.size() == 5

        when:
        cache.evictAll('foo', ['bar0', 'bar1'])
        allFoo = cache.getAll('foo')

        then:
        allFoo != null
        allFoo.size() == 3

        when:
        cache.evictAll('foo', ['bar2', 'bar3', 'bar4'])
        allFoo = cache.getAll('foo')

        then:
        allFoo != null
        allFoo.isEmpty()

        when:
        cache.evictAll('foo', ['bar0', 'bar1'])

        then:
        noExceptionThrown()
    }
}
