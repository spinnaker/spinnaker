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

import com.netflix.spinnaker.cats.test.ManualRunnableScheduler
import spock.lang.Specification
import spock.lang.Subject

import java.util.concurrent.TimeUnit

class DefaultAgentSchedulerSpec extends Specification {

    @Subject
    DefaultAgentScheduler scheduler
    ManualRunnableScheduler runnableScheduler

    def 'executionInstrumentation is informed of agent execution'() {
        setup:
        def agent = Stub(CachingAgent)
        def instr = Mock(ExecutionInstrumentation)
        def exec = Mock(AgentExecution)
        runnableScheduler = new ManualRunnableScheduler()
        scheduler = new DefaultAgentScheduler(runnableScheduler, 1, TimeUnit.SECONDS)

        when:
        scheduler.schedule(agent, exec, instr)
        runnableScheduler.runAll()

        then:
        1 * instr.executionStarted(agent)
        1 * exec.executeAgent(agent)
        1 * instr.executionCompleted(agent, _ )
        0 * _
    }

    def 'executionInstrumentation is informed of agent failure'() {
        setup:
        def agent = Stub(CachingAgent)
        def instr = Mock(ExecutionInstrumentation)
        def exec = Mock(AgentExecution)
        runnableScheduler = new ManualRunnableScheduler()
        scheduler = new DefaultAgentScheduler(runnableScheduler, 1, TimeUnit.SECONDS)
        def cause = new RuntimeException('failboat')

        when:
        scheduler.schedule(agent, exec, instr)
        runnableScheduler.runAll()

        then:
        1 * instr.executionStarted(agent)
        1 * exec.executeAgent(agent) >> { throw cause }
        1 * instr.executionFailed(agent, cause)
        0 * _
    }

}
