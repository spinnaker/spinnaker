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
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import rx.functions.Action0
import rx.schedulers.Schedulers

import javax.annotation.PostConstruct
import java.util.concurrent.TimeUnit

@Slf4j
class RxCachingAgentScheduler implements CachingAgentScheduler {

  @Value('${cachingInterval:30000}')
  long cachingInterval

  @Autowired
  List<CachingAgent> cachingAgents

  @PostConstruct
  void init() {
    for (agent in cachingAgents) {
      Schedulers.computation().createWorker().schedulePeriodically(new CachingAgentRxAdapter(cachingAgent: agent), 0, cachingInterval * agent.intervalMultiplier,
          TimeUnit.MILLISECONDS)
    }
  }

  private static class CachingAgentRxAdapter implements Action0 {
    CachingAgent cachingAgent

    void call() {
        try {
            cachingAgent.call()
        } catch (e) {
            log.warn("Caching failed for $cachingAgent.description", e)
        }
    }
  }
}
