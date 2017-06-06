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
import spock.lang.Specification

import java.util.concurrent.TimeoutException

class PooledRequestQueueSpec extends Specification {
  def "should execute requests"() {
    given:
    def queue = new PooledRequestQueue(new NoopRegistry(), 10, 1)

    when:
    Long result = queue.execute("foo", { return 12345L })

    then:
    result == 12345L
  }

  def "should time out if request does not complete"() {
    given:
    def queue = new PooledRequestQueue(new NoopRegistry(), 10, 1)

    when:
    Long result = queue.execute("foo", { Thread.sleep(20); return 12345L })

    then:
    thrown(TimeoutException)
  }
}
