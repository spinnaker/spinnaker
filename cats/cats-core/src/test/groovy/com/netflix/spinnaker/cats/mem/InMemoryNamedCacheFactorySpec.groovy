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

package com.netflix.spinnaker.cats.mem

import spock.lang.Specification
import spock.lang.Subject

class InMemoryNamedCacheFactorySpec extends Specification {
    @Subject
    InMemoryNamedCacheFactory factory = new InMemoryNamedCacheFactory()

    def 'unique cache returned per key'() {
        when:
        def c1 = factory.getCache('c1')
        def c2 = factory.getCache('c2')

        then:
        !c1.is(c2)
    }

    def 'same cache returned for same key'() {
        setup:
        String key = 'cash'
        when:
        def c1 = factory.getCache(key)
        def c2 = factory.getCache(key)

        then:
        c1.is(c2)
    }
}
