/*
 * Copyright 2022 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.fiat.roles

import spock.lang.Specification

class CallableCacheSpec extends Specification {

    private static final String CACHE_KEY_A = "A"
    private static final String CACHE_KEY_B = "B"

    def "should execute callable when cache doesn't contain key"() {
        setup:
        def cache = new CallableCache<String, Long>()
        when:
        def firstExecution = cache.runAndGetResult(CACHE_KEY_A, waitAndReturnValueCallable())
        def secondExecution = cache.runAndGetResult(CACHE_KEY_B, waitAndReturnValueCallable())
        then:
        firstExecution != secondExecution
    }

    def "should not execute callable when cache already contains the key"() {
        setup:
        def cache = new CallableCache<String, Long>()
        when:
        def firstExecution = cache.runAndGetResult(CACHE_KEY_A, waitAndReturnValueCallable())
        def secondExecution = cache.runAndGetResult(CACHE_KEY_A, waitAndReturnValueCallable())
        then:
        firstExecution == secondExecution
    }

    def "should not remove cache entry when execution is still in progress"() {
        setup:
        def cache = new CallableCache<String, Long>()
        when:
        def firstExecution = cache.runAndGetResult(CACHE_KEY_A, waitAndReturnValueCallable())
        cache.clear(CACHE_KEY_A)
        def secondExecution = cache.runAndGetResult(CACHE_KEY_A, waitAndReturnValueCallable())
        then:
        firstExecution == secondExecution
    }

    def "should remove cache entry when execution is done"() {
        setup:
        def cache = new CallableCache<String, Long>()
        when:
        def firstExecution = cache.runAndGetResult(CACHE_KEY_A, returnValueCallable())
        sleep(100)
        cache.clear(CACHE_KEY_A)
        def secondExecution = cache.runAndGetResult(CACHE_KEY_A, waitAndReturnValueCallable())
        then:
        firstExecution != secondExecution
    }

    def "should not throw exception when null key is supplied to clear"() {
        setup:
        def cache = new CallableCache<String, Long>()
        when:
        cache.clear(null)

        then:
        noExceptionThrown()
    }

    def returnValueCallable() {
        return { -> 1L }
    }

    def waitAndReturnValueCallable() {
        return { ->
            Thread.sleep(10000)
            return 1L
        }
    }


}
