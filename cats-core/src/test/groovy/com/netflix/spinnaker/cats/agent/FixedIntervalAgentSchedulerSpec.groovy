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

import spock.lang.Specification
import spock.lang.Subject

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class FixedIntervalAgentSchedulerSpec extends Specification {
    //instrumentation is called appropriately

    @Subject FixedIntervalAgentScheduler scheduler

    def 'executionInstrumentation is informed of agent execution'() {
        setup:
        def agent = Stub(CachingAgent)
        def instr = Mock(ExecutionInstrumentation)
        def exec = Mock(AgentExecution)
        scheduler = new FixedIntervalAgentScheduler(100, TimeUnit.DAYS)
        def latch = new CountDownLatch(1)

        when:
        scheduler.schedule(agent, exec, instr)
        def acquiredLatch = latch.await(200, TimeUnit.MILLISECONDS)

        then:
        1 * instr.executionStarted(agent)
        1 * exec.executeAgent(agent)
        1 * instr.executionCompleted(agent) >> {
            latch.countDown()
        }
        0 * _
        acquiredLatch

        cleanup:
        scheduler.shutdown()
    }

    def 'executionInstrumentation is informed of agent failure'() {
        setup:
        def agent = Stub(CachingAgent)
        def instr = Mock(ExecutionInstrumentation)
        def exec = Mock(AgentExecution)
        scheduler = new FixedIntervalAgentScheduler(100, TimeUnit.DAYS)
        def cause = new RuntimeException('failboat')
        def latch = new CountDownLatch(1)

        when:
        scheduler.schedule(agent, exec, instr)
        def acquiredLatch = latch.await(200, TimeUnit.MILLISECONDS)

        then:
        1 * instr.executionStarted(agent)
        1 * exec.executeAgent(agent) >> { throw cause }
        1 * instr.executionFailed(agent, cause) >> {
            latch.countDown()
        }
        0 * _
        acquiredLatch

        cleanup:
        scheduler.shutdown()
    }

}
