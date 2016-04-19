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

package com.netflix.spinnaker.cats.test

import com.netflix.spinnaker.cats.agent.Agent
import com.netflix.spinnaker.cats.agent.AgentExecution
import com.netflix.spinnaker.cats.agent.AgentLock
import com.netflix.spinnaker.cats.agent.AgentScheduler
import com.netflix.spinnaker.cats.agent.ExecutionInstrumentation
import com.netflix.spinnaker.cats.module.CatsModuleAware

class TestScheduler extends CatsModuleAware implements AgentScheduler<AgentLock> {
    Collection<Scheduled> scheduled = []

    @Override
    void schedule(Agent agent, AgentExecution agentExecution, ExecutionInstrumentation executionInstrumentation) {
        scheduled << new Scheduled(agent, agentExecution)
    }

    @Override
    void unschedule(Agent agent) {
        scheduled.remove(scheduled.find { it.agent == agent })
    }

    void runAll() {
        for (Scheduled s : scheduled) {
            s.exec.executeAgent(s.agent)
        }
    }

    private static class Scheduled {
        Agent agent
        AgentExecution exec

        Scheduled(Agent agent, AgentExecution exec) {
            this.agent = agent
            this.exec = exec
        }
    }
}
