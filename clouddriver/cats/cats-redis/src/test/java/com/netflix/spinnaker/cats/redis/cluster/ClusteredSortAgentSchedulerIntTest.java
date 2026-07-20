/*
 * Copyright 2025 Wise, PLC.
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

package com.netflix.spinnaker.cats.redis.cluster;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.netflix.spinnaker.cats.agent.Agent;
import com.netflix.spinnaker.cats.agent.CachingAgent;
import com.netflix.spinnaker.cats.agent.ExecutionInstrumentation;
import com.netflix.spinnaker.cats.cluster.AgentIntervalProvider;
import com.netflix.spinnaker.cats.cluster.NodeStatusProvider;
import com.netflix.spinnaker.cats.test.ManualRunnableScheduler;
import com.netflix.spinnaker.cats.test.MockAgentLongRunningExecution;
import com.netflix.spinnaker.kork.jedis.EmbeddedRedis;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ClusteredSortAgentSchedulerIntTest {

  private static EmbeddedRedis embeddedRedis;
  private static AgentIntervalProvider intervalProvider;

  @BeforeAll
  static void setup() {
    embeddedRedis = EmbeddedRedis.embed();
    intervalProvider = mock(AgentIntervalProvider.class);
    when(intervalProvider.getInterval(any(Agent.class)))
        .thenReturn(new AgentIntervalProvider.Interval(2000L, 2000L));
  }

  @AfterAll
  static void cleanup() {
    if (embeddedRedis != null) {
      embeddedRedis.destroy();
    }
  }

  @BeforeEach
  public void setUpTest() {
    embeddedRedis.getPool().getResource().flushAll();
  }

  @Test
  void longRunningAgentIsRescheduledAfterFailure() throws InterruptedException {
    String agentType = "someagent-0";

    CachingAgent agent = mock(CachingAgent.class);
    ExecutionInstrumentation instr = mock(ExecutionInstrumentation.class);
    MockAgentLongRunningExecution exec = spy(new MockAgentLongRunningExecution());

    NodeStatusProvider nodeStatusProvider = mock(NodeStatusProvider.class);
    when(nodeStatusProvider.isNodeEnabled()).thenReturn(true);
    when(agent.getAgentType()).thenReturn(agentType);

    ManualRunnableScheduler runnableScheduler = new ManualRunnableScheduler();
    ManualRunnableScheduler runnableExecutor = new ManualRunnableScheduler();

    ClusteredSortAgentScheduler scheduler =
        new ClusteredSortAgentScheduler(
            embeddedRedis.getPool(),
            nodeStatusProvider,
            intervalProvider,
            10,
            runnableExecutor,
            runnableScheduler);

    scheduler.schedule(agent, exec, instr);
    runnableScheduler.runAll();
    runnableExecutor.runAll();

    exec.fail();
    runnableScheduler.runAll(); // pushes element back to work queue
    Thread.sleep(3000); // gives enough time to expire across Redis second boundaries

    runnableScheduler.runAll(); // lets it be rescheduled
    runnableExecutor.runAll();

    verify(exec, times(2)).executeAgent(any());
    verify(exec, times(1)).stopExecutingAndCleanup();
  }

  @Test
  void longRunningAgentIsNotRescheduledInDifferentNodeIfStillRunning() throws InterruptedException {
    String agentType = "someagent-1";

    CachingAgent agent1 = mock(CachingAgent.class);
    ExecutionInstrumentation instr1 = mock(ExecutionInstrumentation.class);
    MockAgentLongRunningExecution exec1 = spy(new MockAgentLongRunningExecution());

    CachingAgent agent2 = mock(CachingAgent.class);
    ExecutionInstrumentation instr2 = mock(ExecutionInstrumentation.class);
    MockAgentLongRunningExecution exec2 = spy(new MockAgentLongRunningExecution());

    NodeStatusProvider nodeStatusProvider1 = mock(NodeStatusProvider.class);
    NodeStatusProvider nodeStatusProvider2 = mock(NodeStatusProvider.class);

    when(nodeStatusProvider1.isNodeEnabled()).thenReturn(true);
    when(agent1.getAgentType()).thenReturn(agentType);
    when(nodeStatusProvider2.isNodeEnabled()).thenReturn(true);
    when(agent2.getAgentType()).thenReturn(agentType);

    ManualRunnableScheduler runnableScheduler1 = new ManualRunnableScheduler();
    ManualRunnableScheduler runnableExecutor1 = new ManualRunnableScheduler();
    ManualRunnableScheduler runnableScheduler2 = new ManualRunnableScheduler();
    ManualRunnableScheduler runnableExecutor2 = new ManualRunnableScheduler();

    ClusteredSortAgentScheduler scheduler1 =
        new ClusteredSortAgentScheduler(
            embeddedRedis.getPool(),
            nodeStatusProvider1,
            intervalProvider,
            10,
            runnableExecutor1,
            runnableScheduler1);

    ClusteredSortAgentScheduler scheduler2 =
        new ClusteredSortAgentScheduler(
            embeddedRedis.getPool(),
            nodeStatusProvider2,
            intervalProvider,
            10,
            runnableExecutor2,
            runnableScheduler2);

    scheduler1.schedule(agent1, exec1, instr1);
    runnableScheduler1.runAll();
    runnableExecutor1.runAll();

    Thread.sleep(3000); // gives enough time to expire
    runnableScheduler1.runAll(); // refreshes item at working queue

    runnableScheduler1.runAll(); // guarantees it is not rescheduled locally
    runnableExecutor1.runAll();

    scheduler2.schedule(agent2, exec2, instr2); // nor it is rescheduled in another node
    runnableScheduler2.runAll();
    runnableExecutor2.runAll();

    verify(exec1, times(1)).executeAgent(any());
    verify(exec2, times(0)).executeAgent(any());
  }
}
