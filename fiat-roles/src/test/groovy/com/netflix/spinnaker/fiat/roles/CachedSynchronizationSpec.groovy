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

import java.util.concurrent.Callable
import java.util.concurrent.CompletableFuture

import static com.netflix.spinnaker.fiat.roles.UserRolesSyncStrategy.CachedSynchronizationStrategy
import static com.netflix.spinnaker.fiat.roles.UserRolesSyncStrategy.RolesSynchronizationException

class CachedSynchronizationSpec extends Specification {

    def "should delegate synchronization to cache"() {
        setup:
        def mockedSynchronizer = Mock(Synchronizer)
        def cachedSynchronization = new CachedSynchronizationStrategy(mockedSynchronizer)
        when:
        cachedSynchronization.syncAndReturn(["rolea", "roleb"])
        then:
        1 * mockedSynchronizer.syncAndReturn(_)
    }

    def "should catch any exception during synchronization and rethrow dedicated exception"() {
        setup:
        def mockedSynchronizer = Mock(Synchronizer)
        mockedSynchronizer.syncAndReturn(_ as List<String>) >> { throw new RuntimeException("Very generic runtime exception") }
        def cachedSynchronization = new CachedSynchronizationStrategy(mockedSynchronizer)
        when:
        cachedSynchronization.syncAndReturn(["rolea", "roleb"])
        then:
        thrown RolesSynchronizationException
    }

    def "should remove element from cache when execution is finished"() {
        setup:
        def mockedCache = Mock(CallableCache)
        def mockedSynchronizer = Mock(Synchronizer)
        def cachedSynchronization = new CachedSynchronizationStrategy(mockedSynchronizer, mockedCache)
        def completableFuture = new CompletableFuture<Long>()
        completableFuture.complete(1L)
        mockedCache.runAndGetResult(_ as List<String>, _ as Callable<Long>) >> completableFuture
        when:
        cachedSynchronization.syncAndReturn(["rolea", "roleb"])
        then:
        1 * mockedCache.clear(["rolea", "roleb"])
    }

    def "should remove element from cache when exception occurred during processing"() {
        setup:
        def mockedCache = Mock(CallableCache)
        def mockedSynchronizer = Mock(Synchronizer)
        mockedSynchronizer.syncAndReturn(_ as List<String>) >> { throw new RuntimeException("Very generic runtime exception") }
        def cachedSynchronization = new CachedSynchronizationStrategy(mockedSynchronizer, mockedCache)
        def completableFuture = new CompletableFuture<Long>()
        completableFuture.complete(1L)
        mockedCache.runAndGetResult(_ as List<String>, _ as Callable<Long>) >> completableFuture
        when:
        cachedSynchronization.syncAndReturn(["rolea", "roleb"])
        then:
        1 * mockedCache.clear(["rolea", "roleb"])
    }

    def "should not fail when the roles list is null"(){
        setup:
        def mockedSynchronizer = Mock(Synchronizer)
        mockedSynchronizer.syncAndReturn(_ as List<String>) >> 1L
        def cachedSynchronization = new CachedSynchronizationStrategy(mockedSynchronizer)

        when:
        cachedSynchronization.syncAndReturn(null)

        then:
        noExceptionThrown()
    }

}
