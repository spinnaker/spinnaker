/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.clouddriver.requestqueue.pooled

import com.netflix.spectator.api.NoopRegistry
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import spock.lang.Specification

import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean

class PooledRequestQueueSpec extends Specification {
  def dynamicConfigService = Mock(DynamicConfigService)

  def "should execute requests"() {
    given:
    def queue = new PooledRequestQueue(dynamicConfigService, new NoopRegistry(), 1000, 1000, 1)

    when:
    Long result = queue.execute("foo", { return 12345L })

    then:
    result == 12345L
  }

  def "should time out if request does not complete"() {
    given:
    def queue = new PooledRequestQueue(dynamicConfigService, new NoopRegistry(), 5000, 10, 1)

    when:
    queue.execute("foo", { Thread.sleep(20); return 12345L })

    then:
    thrown(PromiseTimeoutException)
  }

  def "should time out if request does not start in time"() {
    given: "a queue with one worker thread"
    def queue = new PooledRequestQueue(dynamicConfigService, new NoopRegistry(), 10, 10, 1)
    AtomicBoolean itRan = new AtomicBoolean(false)
    Callable<Void> didItRun = {
      itRan.set(true)
    }

    when: "we start up a thread that blocks the pool"
    def latch = new CountDownLatch(1)
    Callable<Void> jerkThread = {
      latch.countDown()
      Thread.sleep(40)
    }

    Thread.start {
      try {
        queue.execute("foo", jerkThread)
      } catch (PromiseTimeoutException) {
        //expected
      }
    }

    and: "try to start another"
    latch.await()
    queue.execute("foo", didItRun)

    then: "the second work is never started"
    thrown(PromiseNotStartedException)
    !itRan.get()
  }
}
