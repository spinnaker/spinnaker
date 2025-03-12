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

package com.netflix.spinnaker.cats.agent

import com.netflix.spinnaker.cats.cache.DefaultCacheData
import com.netflix.spinnaker.cats.provider.Provider
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.cats.provider.ProviderRegistry
import com.netflix.spinnaker.cats.test.TestAgent
import com.netflix.spinnaker.cats.test.TestProvider
import com.netflix.spinnaker.cats.test.TestProviderRegistry
import com.netflix.spinnaker.cats.test.TestScheduler
import spock.lang.Specification

class AgentControllerSpec extends Specification {
    def 'should schedule all agents in all providers'() {
        setup:
        def p1 = Mock(Provider)
        def a1 = Stub(CachingAgent)

        def p2 = Mock(Provider)
        def a2 = Stub(CachingAgent)

        def registry = Stub(ProviderRegistry) {
            getProviders() >> [p1, p2]
        }

        def sched = Mock(AgentScheduler)

        def instr = Stub(ExecutionInstrumentation)

        when:
        new AgentController(registry, sched, instr)

        then:
        1 * p1.getAgents() >> [a1]
        1 * sched.schedule(a1, _ as AgentExecution, instr)
        1 * p2.getAgents() >> [a2]
        1 * sched.schedule(a2, _ as AgentExecution, instr)
        0 * _
    }

    def 'should store cache result with correct authoritative'() {
        setup:
        def a = new TestAgent()
        a.authoritative << 'serverGroup'
        a.types << 'application'
        a.results['serverGroup'] << new DefaultCacheData('sg', [foo: 'bar'], [:])
        a.results['application'] << new DefaultCacheData('app', [:], [:])

        def p = new TestProvider(a)
        def pc = Mock(ProviderCache)
        def reg = new TestProviderRegistry(p, pc)
        def sched = new TestScheduler()

        when:
        new AgentController(reg, sched, new NoopExecutionInstrumentation())

        then:
        sched.scheduled.size() == 1

        when:
        sched.runAll()

        then:
        1 * pc.putCacheResult(_ as String, _ as Collection<String>, _ as CacheResult) >> { source, auth, CacheResult res ->
            assert source == a.agentType
            assert auth.size() == 1
            assert auth.contains('serverGroup')
            assert res.cacheResults.size() == 2
            assert res.cacheResults['serverGroup'].size() == 1
            assert res.cacheResults['application'].size() == 1
        }
    }

}
