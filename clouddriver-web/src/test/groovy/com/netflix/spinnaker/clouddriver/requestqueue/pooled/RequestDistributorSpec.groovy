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
import com.netflix.spectator.api.Registry
import spock.lang.Specification

import java.util.concurrent.Executor
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class RequestDistributorSpec extends Specification {

  def "should pop and dispatch one item per queue"() {
    given:
    Registry registry = new NoopRegistry()
    Collection<Queue<PooledRequest>> queues = [new LinkedBlockingQueue<>(), new LinkedBlockingQueue<>(), new LinkedBlockingQueue<>()]
    queues[0].add(new PooledRequest<Integer>(registry, "appA", {return 0}))
    queues[0].add(new PooledRequest<Integer>(registry, "appA", {return 1}))
    queues[2].add(new PooledRequest<Integer>(registry, "appC", {return 2}))
    def coord = Mock(PollCoordinator)
    List<PooledRequest<Integer>> reqs = []
    def exec = Stub(Executor) {
      execute(_) >> { Runnable r ->
        reqs.add(r)
        r.run()
      }
    }

    RequestDistributor dist = new RequestDistributor(registry, coord, exec, queues)

    when:
    dist.processPartitions()

    then:
    1 * coord.reset()
    1 * coord.waitForItems(true)
    0 * _

    reqs.size() == 2
    reqs[0].getPromise().blockingGetOrThrow(1, TimeUnit.MILLISECONDS) == 0
    reqs[1].getPromise().blockingGetOrThrow(1, TimeUnit.MILLISECONDS) == 2

  }
}
