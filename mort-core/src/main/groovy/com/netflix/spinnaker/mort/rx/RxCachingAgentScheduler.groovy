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

package com.netflix.spinnaker.mort.rx

import com.netflix.spinnaker.mort.model.CachingAgent
import com.netflix.spinnaker.mort.model.CachingAgentScheduler
import java.util.concurrent.TimeUnit
import javax.annotation.PostConstruct
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import rx.Scheduler.Worker
import rx.functions.Action0
import rx.schedulers.Schedulers

class RxCachingAgentScheduler implements CachingAgentScheduler {

  private static final Worker worker = Schedulers.computation().createWorker()

  @Value('${cachingInterval:30000}')
  long cachingInterval

  @Autowired
  List<CachingAgent> cachingAgents

  @PostConstruct
  void init() {
    for (agent in cachingAgents) {
      worker.schedulePeriodically(new CachingAgentRxAdapter(cachingAgent: agent), 0, cachingInterval,
          TimeUnit.MILLISECONDS)
    }
  }

  private static class CachingAgentRxAdapter implements Action0 {
    CachingAgent cachingAgent

    void call() {
      cachingAgent.call()
    }
  }
}
