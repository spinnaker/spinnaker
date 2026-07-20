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

package com.netflix.spinnaker.cats.sql.cluster

import com.netflix.spinnaker.cats.agent.Agent
import com.netflix.spinnaker.cats.agent.AgentExecution
import com.netflix.spinnaker.cats.agent.CachingAgent
import com.netflix.spinnaker.cats.agent.ExecutionInstrumentation
import com.netflix.spinnaker.cats.cluster.AgentIntervalProvider
import com.netflix.spinnaker.cats.cluster.NodeIdentity
import com.netflix.spinnaker.cats.cluster.NodeStatusProvider
import com.netflix.spinnaker.cats.test.ManualRunnableScheduler
import com.netflix.spinnaker.cats.test.MockAgentLongRunningExecution
import com.netflix.spinnaker.cats.test.MockShardingFilter
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import com.netflix.spinnaker.kork.sql.test.SqlTestUtil
import org.jooq.DSLContext
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.testcontainers.DockerClientFactory
import spock.lang.Requires
import spock.lang.Specification
import spock.util.time.MutableClock

import java.time.Duration

import static org.jooq.impl.DSL.field
import static org.jooq.impl.DSL.table

@Requires({ DockerClientFactory.instance().isDockerAvailable() })
abstract class SqlClusteredAgentSchedulerSpec extends Specification {
  AgentIntervalProvider intervalProvider
  DynamicConfigService dynamicConfigService

  def enabledAgentPattern = ".*"
  List<String> disabledAgentsConfig = Collections.emptyList()

  static SqlTestUtil.TestDatabase testDatabase
  static DSLContext context
  abstract SqlTestUtil.TestDatabase getDatabase()

  @BeforeEach
  void setup() {
    if (testDatabase == null) {
      testDatabase = getDatabase()
      context = testDatabase.context
    }
    intervalProvider = Stub(AgentIntervalProvider)
    dynamicConfigService = Stub(DynamicConfigService)

    intervalProvider.getInterval(_ as Agent) >> new AgentIntervalProvider.Interval(500L, 500L)
    dynamicConfigService.getConfig(*_) >> { args -> getConfig(args[1]) }
  }

  @AfterEach
  void cleanup() {
    if (testDatabase != null) {
      SqlTestUtil.cleanupDb(testDatabase.context)
    }
  }

  @AfterAll
  static void destroyDatabase() {
    if (testDatabase != null) {
      testDatabase.close()
    }
  }

  def getConfig(entry) {
    if (entry == "sql.agent.zombie-threshold-ms") {
      return 3600000L
    }
    else if (entry == "sql.agent.max-concurrent-agents") {
      return 100
    }
    else if (entry == "sql.agent.disabled-agents") {
      return ""
    }
    else if (entry == "sql.agent.release-threshold-ms") {
      return 500L
    }
  }

  def 'longRunningAgent is rescheduled after failure'() {
    given:
    def ctx = createSchedulerContext("node-0", "someagent-0", 0)

    when:
    ctx.scheduler.schedule(ctx.agent, ctx.exec, ctx.instr)
    runSchedulerCycle(ctx)
    ctx.exec.fail()
    ctx.clock.plus(Duration.ofMillis(600))
    runSchedulerCycle(ctx)

    then:
    2 * ctx.exec.executeAgent(_)
    1 * ctx.exec.stopExecutingAndCleanup()

  }

  def 'longRunningAgent isn\'t rescheduled if still running but node topology changes'() {
    given:
    def ctx1 = createSchedulerContext("node1", "someagent-1", 0)
    def ctx2 = createSchedulerContext("node2", "someagent-1", 0)
    ctx2.shardingFilter.remove("someagent-1")

    when:
    ctx1.scheduler.schedule(ctx1.agent, ctx1.exec, ctx1.instr)
    runSchedulerCycle(ctx1)

    ctx1.clock.plus(Duration.ofMillis(100))
    ctx2.clock.plus(Duration.ofMillis(100))

    ctx1.shardingFilter.remove("someagent-1")
    ctx2.shardingFilter.add("someagent-1")
    runSchedulerCycle(ctx1)

    ctx2.scheduler.schedule(ctx2.agent, ctx2.exec, ctx2.instr)
    runSchedulerCycle(ctx2)

    then:
    1 * ctx1.exec.executeAgent(_)
    0 * ctx1.exec.stopExecutingAndCleanup()
    0 * ctx2.exec.executeAgent(_)
  }

  def 'longRunningAgent is rescheduled if stopped and node topology changes'() {
    given:
    def ctx1 = createSchedulerContext("node1", "someagent-3", 0)
    def ctx2 = createSchedulerContext("node2", "someagent-3", 0)
    ctx2.shardingFilter.remove("someagent-3")

    when:
    ctx1.scheduler.schedule(ctx1.agent, ctx1.exec, ctx1.instr)
    ctx2.scheduler.schedule(ctx2.agent, ctx2.exec, ctx2.instr)

    runSchedulerCycle(ctx1)
    runSchedulerCycle(ctx2)

    ctx1.exec.fail()
    ctx1.clock.plus(Duration.ofMillis(100))
    ctx2.clock.plus(Duration.ofMillis(100))

    ctx1.shardingFilter.remove("someagent-3")
    ctx2.shardingFilter.add("someagent-3")

    runSchedulerCycle(ctx1)
    runSchedulerCycle(ctx2)

    then:
    1 * ctx1.exec.executeAgent(_)
    1 * ctx1.exec.stopExecutingAndCleanup()
    1 * ctx2.exec.executeAgent(_)
  }

  def 'longRunningAgent is rescheduled if node topology changes and still running but above rebalance threshold'() {
    given:
    SqlClusteredAgentScheduler scheduler1
    ManualRunnableScheduler runnableScheduler1
    ManualRunnableScheduler runnableExecutor1
    def agentType1 = "someagenttype-1"
    def agentType2 = "someagenttype-2"

    // node 1
    def agent1 = Stub(CachingAgent)
    agent1.getAgentType() >> agentType1
    def instr1 = Mock(ExecutionInstrumentation)
    def exec1 = Spy(MockAgentLongRunningExecution)

    def agent2 = Stub(CachingAgent)
    agent2.getAgentType() >> agentType2
    def instr2 = Mock(ExecutionInstrumentation)
    def exec2 = Spy(MockAgentLongRunningExecution)

    def nodeIdentity1 = Stub(NodeIdentity)
    def nodeStatusProvider1 = Stub(NodeStatusProvider)
    def shardingFilter1 = new MockShardingFilter()

    nodeIdentity1.nodeIdentity >> "node1"
    nodeStatusProvider1.isNodeEnabled() >> true
    shardingFilter1.add(agentType1)
    shardingFilter1.add(agentType2)

    runnableScheduler1 = new ManualRunnableScheduler()
    runnableExecutor1 = new ManualRunnableScheduler()

    def clock = new MutableClock()

    scheduler1 = new SqlClusteredAgentScheduler(context, nodeIdentity1, intervalProvider, nodeStatusProvider1, dynamicConfigService, enabledAgentPattern, disabledAgentsConfig, 500, null,
      runnableExecutor1, runnableScheduler1,
      shardingFilter1, 50, clock)


    when:
    scheduler1.schedule(agent1, exec1, instr1)
    scheduler1.schedule(agent2, exec2, instr2)
    runnableScheduler1.runAll()
    runnableExecutor1.runAll()

    clock.plus(Duration.ofMillis(600))

    shardingFilter1.remove(agentType2)

    runnableScheduler1.runAll()
    runnableExecutor1.runAll()

    then:
    1 * exec1.executeAgent(_)
    1 * exec2.executeAgent(_)
    1 * exec2.stopExecutingAndCleanup()
  }

  def 'longRunningAgent is not rescheduled if node topology changes and still running but below rebalance threshold'() {
    given:
    SqlClusteredAgentScheduler scheduler1
    ManualRunnableScheduler runnableScheduler1
    ManualRunnableScheduler runnableExecutor1
    def agentType1 = "someagenttype-1b"
    def agentType2 = "someagenttype-2b"

    // node 1
    def agent1 = Stub(CachingAgent)
    agent1.getAgentType() >> agentType1
    def instr1 = Mock(ExecutionInstrumentation)
    def exec1 = Spy(MockAgentLongRunningExecution)

    def agent2 = Stub(CachingAgent)
    agent2.getAgentType() >> agentType2
    def instr2 = Mock(ExecutionInstrumentation)
    def exec2 = Spy(MockAgentLongRunningExecution)

    def nodeIdentity1 = Stub(NodeIdentity)
    def nodeStatusProvider1 = Stub(NodeStatusProvider)
    def shardingFilter1 = new MockShardingFilter()

    nodeIdentity1.nodeIdentity >> "node1"
    nodeStatusProvider1.isNodeEnabled() >> true
    shardingFilter1.add(agentType1)
    shardingFilter1.add(agentType2)

    runnableScheduler1 = new ManualRunnableScheduler()
    runnableExecutor1 = new ManualRunnableScheduler()

    def clock = new MutableClock()

    scheduler1 = new SqlClusteredAgentScheduler(context, nodeIdentity1, intervalProvider, nodeStatusProvider1, dynamicConfigService, enabledAgentPattern, disabledAgentsConfig, 500, null,
      runnableExecutor1, runnableScheduler1,
      shardingFilter1, 300, clock)


    when:
    scheduler1.schedule(agent1, exec1, instr1)
    scheduler1.schedule(agent2, exec2, instr2)
    runnableScheduler1.runAll()
    runnableExecutor1.runAll()

    clock.plus(Duration.ofMillis(600))

    shardingFilter1.remove(agentType2)

    runnableScheduler1.runAll()
    runnableExecutor1.runAll()

    then:
    1 * exec1.executeAgent(_)
    1 * exec2.executeAgent(_)
    0 * exec2.stopExecutingAndCleanup()
  }

  def 'longRunningAgent is rescheduled if node topology was changed due to rebalance threshold but agent gets reassigned'() {
    given:
    SqlClusteredAgentScheduler scheduler1
    ManualRunnableScheduler runnableScheduler1
    ManualRunnableScheduler runnableExecutor1
    def agentType1 = "someagenttype-1c"
    def agentType2 = "someagenttype-2c"

    // node 1
    def agent1 = Stub(CachingAgent)
    agent1.getAgentType() >> agentType1
    def instr1 = Mock(ExecutionInstrumentation)
    def exec1 = Spy(MockAgentLongRunningExecution)

    def agent2 = Stub(CachingAgent)
    agent2.getAgentType() >> agentType2
    def instr2 = Mock(ExecutionInstrumentation)
    def exec2 = Spy(MockAgentLongRunningExecution)

    def nodeIdentity1 = Stub(NodeIdentity)
    def nodeStatusProvider1 = Stub(NodeStatusProvider)
    def shardingFilter1 = new MockShardingFilter()

    nodeIdentity1.nodeIdentity >> "node1"
    nodeStatusProvider1.isNodeEnabled() >> true
    shardingFilter1.add(agentType1)
    shardingFilter1.add(agentType2)

    runnableScheduler1 = new ManualRunnableScheduler()
    runnableExecutor1 = new ManualRunnableScheduler()

    def clock = new MutableClock()

    scheduler1 = new SqlClusteredAgentScheduler(context, nodeIdentity1, intervalProvider, nodeStatusProvider1, dynamicConfigService, enabledAgentPattern, disabledAgentsConfig, 500, null,
      runnableExecutor1, runnableScheduler1,
      shardingFilter1, 50, clock)


    when:
    scheduler1.schedule(agent1, exec1, instr1)
    scheduler1.schedule(agent2, exec2, instr2)
    runnableScheduler1.runAll()
    runnableExecutor1.runAll()

    clock.plus(Duration.ofMillis(600))

    shardingFilter1.remove(agentType2)

    runnableScheduler1.runAll()
    runnableExecutor1.runAll()

    shardingFilter1.add(agentType2)

    runnableScheduler1.runAll()
    runnableExecutor1.runAll()

    then:
    1 * exec1.executeAgent(_)
    2 * exec2.executeAgent(_)
    1 * exec2.stopExecutingAndCleanup()
  }

  def 'should stop long running agents before rescheduling'() {
    given:
    SqlClusteredAgentScheduler scheduler1
    ManualRunnableScheduler runnableScheduler1
    ManualRunnableScheduler runnableExecutor1
    def agentType1 = "test-agent-type-123"

    // old agent execution
    def agent1 = Stub(CachingAgent)
    agent1.getAgentType() >> agentType1
    def instr1 = Mock(ExecutionInstrumentation)
    def exec1 = Spy(MockAgentLongRunningExecution)

    // updated agent execution
    def instr2 = Mock(ExecutionInstrumentation)
    def exec2 = Spy(MockAgentLongRunningExecution)

    def nodeIdentity1 = Stub(NodeIdentity)
    def nodeStatusProvider1 = Stub(NodeStatusProvider)
    def shardingFilter1 = new MockShardingFilter()

    nodeIdentity1.nodeIdentity >> "node1"
    nodeStatusProvider1.isNodeEnabled() >> true
    shardingFilter1.add(agentType1)

    runnableScheduler1 = new ManualRunnableScheduler()
    runnableExecutor1 = new ManualRunnableScheduler()

    def clock = new MutableClock()

    scheduler1 = new SqlClusteredAgentScheduler(context, nodeIdentity1, intervalProvider, nodeStatusProvider1, dynamicConfigService, enabledAgentPattern, disabledAgentsConfig, 500, null,
      runnableExecutor1, runnableScheduler1,
      shardingFilter1, 50, clock)


    when:
    scheduler1.schedule(agent1, exec1, instr1)
    runnableScheduler1.runAll()
    runnableExecutor1.runAll()

    then:
    1 * exec1.executeAgent(_)

    when:
    scheduler1.schedule(agent1, exec2, instr2)
    runnableScheduler1.runAll()
    runnableExecutor1.runAll()

    then:
    1 * exec1.stopExecutingAndCleanup()
    1 * exec2.executeAgent(_)
  }


  def 'Regular Agent is executed in different nodes if topology changes'() {
    given:
    SqlClusteredAgentScheduler scheduler1
    ManualRunnableScheduler runnableScheduler1
    ManualRunnableScheduler runnableExecutor1
    SqlClusteredAgentScheduler scheduler2
    ManualRunnableScheduler runnableScheduler2
    ManualRunnableScheduler runnableExecutor2
    def agentType = "someagent-4"


    // node 1
    def agent1 = Stub(CachingAgent)
    def instr1 = Mock(ExecutionInstrumentation)
    def exec1 = Mock(AgentExecution)
    def nodeIdentity1 = Stub(NodeIdentity)
    def nodeStatusProvider1 = Stub(NodeStatusProvider)
    def shardingFilter1 = new MockShardingFilter()

    nodeIdentity1.nodeIdentity >> "node1"
    nodeStatusProvider1.isNodeEnabled() >> true
    shardingFilter1.add(agentType)
    agent1.getAgentType() >> agentType

    runnableScheduler1 = new ManualRunnableScheduler()
    runnableExecutor1 = new ManualRunnableScheduler()

    def clock1 = new MutableClock()

    scheduler1 = new SqlClusteredAgentScheduler(context, nodeIdentity1, intervalProvider, nodeStatusProvider1, dynamicConfigService, enabledAgentPattern, disabledAgentsConfig, 500, null,
      runnableExecutor1, runnableScheduler1,
      shardingFilter1, 0, clock1)

    // node 2
    def agent2 = Stub(CachingAgent)
    def instr2 = Mock(ExecutionInstrumentation)
    def exec2 = Mock(AgentExecution)
    def nodeIdentity2 = Stub(NodeIdentity)
    def nodeStatusProvider2 = Stub(NodeStatusProvider)
    def shardingFilter2 = new MockShardingFilter()

    nodeIdentity2.nodeIdentity >> "node2"
    nodeStatusProvider2.isNodeEnabled() >> true
    agent2.getAgentType() >> agentType


    runnableScheduler2 = new ManualRunnableScheduler()
    runnableExecutor2 = new ManualRunnableScheduler()

    def clock2 = new MutableClock()

    scheduler2 = new SqlClusteredAgentScheduler(context, nodeIdentity2, intervalProvider, nodeStatusProvider2, dynamicConfigService, enabledAgentPattern, disabledAgentsConfig, 500, null,
      runnableExecutor2, runnableScheduler2,
      shardingFilter2, 0, clock2)


    when:
    scheduler1.schedule(agent1, exec1, instr1)
    runnableScheduler1.runAll()
    runnableExecutor1.runAll()

    clock1.plus(Duration.ofMillis(600))
    clock2.plus(Duration.ofMillis(600))

    shardingFilter1.remove(agentType)
    shardingFilter2.add(agentType)
    runnableScheduler1.runAll()
    runnableExecutor1.runAll()

    scheduler2.schedule(agent2, exec2, instr2)
    runnableScheduler2.runAll()
    runnableExecutor2.runAll()

    then:
    1 * exec1.executeAgent(_)
    1 * exec2.executeAgent(_)
  }



  def 'Regular Agent is not executed in different nodes if topology changes but timeout is not elapsed'() {
    given:
    SqlClusteredAgentScheduler scheduler1
    ManualRunnableScheduler runnableScheduler1
    ManualRunnableScheduler runnableExecutor1
    SqlClusteredAgentScheduler scheduler2
    ManualRunnableScheduler runnableScheduler2
    ManualRunnableScheduler runnableExecutor2
    def agentType = "someagent-5"

    def biggerIntervalProvider = Stub(AgentIntervalProvider)
    biggerIntervalProvider.getInterval(_ as Agent) >> new AgentIntervalProvider.Interval(5000L, 5000L)


    // node 1
    def agent1 = Stub(CachingAgent)
    def instr1 = Mock(ExecutionInstrumentation)
    def exec1 = Mock(AgentExecution)
    def nodeIdentity1 = Stub(NodeIdentity)
    def nodeStatusProvider1 = Stub(NodeStatusProvider)
    def shardingFilter1 = new MockShardingFilter()

    nodeIdentity1.nodeIdentity >> "node1"
    nodeStatusProvider1.isNodeEnabled() >> true
    shardingFilter1.add(agentType)
    agent1.getAgentType() >> agentType

    runnableScheduler1 = new ManualRunnableScheduler()
    runnableExecutor1 = new ManualRunnableScheduler()

    def clock1 = new MutableClock()

    scheduler1 = new SqlClusteredAgentScheduler(context, nodeIdentity1, biggerIntervalProvider, nodeStatusProvider1, dynamicConfigService, enabledAgentPattern, disabledAgentsConfig, 500, null,
      runnableExecutor1, runnableScheduler1,
      shardingFilter1, 0, clock1)

    // node 2
    def agent2 = Stub(CachingAgent)
    def instr2 = Mock(ExecutionInstrumentation)
    def exec2 = Mock(AgentExecution)
    def nodeIdentity2 = Stub(NodeIdentity)
    def nodeStatusProvider2 = Stub(NodeStatusProvider)
    def shardingFilter2 = new MockShardingFilter()

    nodeIdentity2.nodeIdentity >> "node2"
    nodeStatusProvider2.isNodeEnabled() >> true
    agent2.getAgentType() >> agentType


    runnableScheduler2 = new ManualRunnableScheduler()
    runnableExecutor2 = new ManualRunnableScheduler()

    def clock2 = new MutableClock()

    scheduler2 = new SqlClusteredAgentScheduler(context, nodeIdentity2, biggerIntervalProvider, nodeStatusProvider2, dynamicConfigService, enabledAgentPattern, disabledAgentsConfig, 500, null,
      runnableExecutor2, runnableScheduler2,
      shardingFilter2, 0, clock2)


    when:
    scheduler1.schedule(agent1, exec1, instr1)
    runnableScheduler1.runAll()
    runnableExecutor1.runAll()

    clock1.plus(Duration.ofMillis(200))
    clock2.plus(Duration.ofMillis(200))

    shardingFilter1.remove(agentType)
    shardingFilter2.add(agentType)
    runnableScheduler1.runAll()
    runnableExecutor1.runAll()

    scheduler2.schedule(agent2, exec2, instr2)
    runnableScheduler2.runAll()
    runnableExecutor2.runAll()

    then:
    1 * exec1.executeAgent(_)
    0 * exec2.executeAgent(_)
  }

  def 'longRunningAgent lock is renewed successfully while running'() {
    given:
    def agentType = "test-agent-renewal-success"
    def ctx = createSchedulerContext("node1", agentType)

    when: 'agent is scheduled and started'
    ctx.scheduler.schedule(ctx.agent, ctx.exec, ctx.instr)
    runSchedulerCycle(ctx)

    then: 'agent is running'
    1 * ctx.exec.executeAgent(_)

    when: 'time passes and scheduler runs again to renew the lock'
    ctx.clock.plus(Duration.ofMillis(600))
    runSchedulerCycle(ctx)

    then: 'lock is renewed and agent continues running without restart'
    0 * ctx.exec.executeAgent(_)
    0 * ctx.exec.stopExecutingAndCleanup()

    and: 'lock still exists in database with this node as owner'
    def lock = context.select(field("owner_id"), field("lock_expiry"))
      .from(table("cats_agent_locks"))
      .where(field("agent_name").eq(agentType))
      .fetchOne()
    lock != null
    lock.get("owner_id") == "node1"
    (lock.get("lock_expiry") as Long) > ctx.clock.millis()
  }

  def 'longRunningAgent is stopped when lock renewal fails'() {
    given:
    def agentType = "test-agent-renewal-failure"
    def ctx = createSchedulerContext("node1", agentType)

    when: 'agent is scheduled and started'
    ctx.scheduler.schedule(ctx.agent, ctx.exec, ctx.instr)
    runSchedulerCycle(ctx)

    then: 'agent is running'
    1 * ctx.exec.executeAgent(_)

    when: 'lock is stolen by another node'
    context.update(table("cats_agent_locks"))
       .set(field("owner_id"), "node2")
      .where(field("agent_name").eq(agentType))
      .execute()

    and: 'time passes and scheduler runs again to renew the lock'
    ctx.clock.plus(Duration.ofMillis(600))
    runSchedulerCycle(ctx)

    then: 'agent is stopped because lock renewal failed'
    1 * ctx.exec.stopExecutingAndCleanup()
  }

  def 'longRunningAgent is properly stopped when unscheduled'() {
    given:
    def agentType = "test-agent-unschedule"
    def ctx = createSchedulerContext("node1", agentType)

    when: 'agent is scheduled and started'
    ctx.scheduler.schedule(ctx.agent, ctx.exec, ctx.instr)
    runSchedulerCycle(ctx)

    then: 'agent is running'
    1 * ctx.exec.executeAgent(_)

    when: 'agent is unscheduled'
    ctx.scheduler.unschedule(ctx.agent)

    and: 'time passes and scheduler runs again'
    ctx.clock.plus(Duration.ofMillis(600))
    runSchedulerCycle(ctx)

    then: 'agent is stopped and cleaned up'
    1 * ctx.exec.stopExecutingAndCleanup()

    and: 'lock is released from database'
    def lock = context.select(field("agent_name"))
      .from(table("cats_agent_locks"))
      .where(field("agent_name").eq(agentType))
      .fetch()
    lock.isEmpty()
  }

  def 'multiple longRunningAgents can be started simultaneously'() {
    given:
    def agentType1 = "test-agent-multi-1"
    def agentType2 = "test-agent-multi-2"
    def agentType3 = "test-agent-multi-3"

    def ctx1 = createSchedulerContext("node1", agentType1)

    def agent2 = Stub(CachingAgent)
    agent2.getAgentType() >> agentType2
    def instr2 = Mock(ExecutionInstrumentation)
    def exec2 = Spy(MockAgentLongRunningExecution)
    ctx1.shardingFilter.add(agentType2)

    def agent3 = Stub(CachingAgent)
    agent3.getAgentType() >> agentType3
    def instr3 = Mock(ExecutionInstrumentation)
    def exec3 = Spy(MockAgentLongRunningExecution)
    ctx1.shardingFilter.add(agentType3)


    when: 'multiple agents are scheduled'
    ctx1.scheduler.schedule(ctx1.agent, ctx1.exec, ctx1.instr)
    ctx1.scheduler.schedule(agent2, exec2, instr2)
    ctx1.scheduler.schedule(agent3, exec3, instr3)
    runSchedulerCycle(ctx1)

    then: 'all agents are started'
    1 * ctx1.exec.executeAgent(_)
    1 * exec2.executeAgent(_)
    1 * exec3.executeAgent(_)

    and: 'all locks exist in database'
    def locks = context.select(field("agent_name"))
      .from(table("cats_agent_locks"))
      .where(field("agent_name").in(agentType1, agentType2, agentType3))
      .fetch()
    locks.size() == 3
  }

  def 'longRunningAgent that fails to acquire lock is retried on next scheduling cycle'() {
    given:
    def agentType = "test-agent-retry-5"
    def ctx1 = createSchedulerContext("node1", agentType)
    def ctx2 = createSchedulerContext("node2", agentType)

    when: 'node1 starts the agent first'
    ctx1.scheduler.schedule(ctx1.agent, ctx1.exec, ctx1.instr)
    runSchedulerCycle(ctx1)

    then: 'node1 agent is running'
    1 * ctx1.exec.executeAgent(_)

    when: 'node2 tries to start the same agent but cannot acquire lock'
    ctx2.scheduler.schedule(ctx2.agent, ctx2.exec, ctx2.instr)
    runSchedulerCycle(ctx2)

    then: 'node2 agent does not start'
    0 * ctx2.exec.executeAgent(_)

    when: 'node1 agent fails and lock expires, time passes'
    ctx1.exec.fail()
    ctx1.shardingFilter.remove(agentType)
    ctx1.clock.plus(Duration.ofMillis(600))
    ctx2.clock.plus(Duration.ofMillis(600))
    runSchedulerCycle(ctx1)
    runSchedulerCycle(ctx2)

    then: 'node1 agent is stopped and node2 agent successfully starts'
    1 * ctx1.exec.stopExecutingAndCleanup()
    1 * ctx2.exec.executeAgent(_)
  }

  def 'longRunningAgent respects sharding filter on startup'() {
    given:
    def agentType = "test-agent-sharding"
    def ctx = createSchedulerContext("node1", agentType)

    when: 'agent is scheduled but not in sharding filter'
    ctx.shardingFilter.remove(agentType)
    ctx.scheduler.schedule(ctx.agent, ctx.exec, ctx.instr)
    runSchedulerCycle(ctx)

    then: 'agent does not start'
    0 * ctx.exec.executeAgent(_)

    when: 'agent is added to sharding filter'
    ctx.shardingFilter.add(agentType)
    runSchedulerCycle(ctx)

    then: 'agent starts'
    1 * ctx.exec.executeAgent(_)
  }

  private SchedulerTestContext createSchedulerContext(String nodeId, String agentType, int rebalanceThreshold = 0) {
    def agent = Stub(CachingAgent)
    agent.getAgentType() >> agentType

    def instr = Mock(ExecutionInstrumentation)
    def exec = Spy(MockAgentLongRunningExecution)

    def nodeIdentity = Stub(NodeIdentity)
    nodeIdentity.nodeIdentity >> nodeId

    def nodeStatusProvider = Stub(NodeStatusProvider)
    nodeStatusProvider.isNodeEnabled() >> true

    def shardingFilter = new MockShardingFilter()
    shardingFilter.add(agentType)

    def runnableScheduler = new ManualRunnableScheduler()
    def runnableExecutor = new ManualRunnableScheduler()

    def clock = new MutableClock()

    def scheduler = new SqlClusteredAgentScheduler(
      context,
      nodeIdentity,
      intervalProvider,
      nodeStatusProvider,
      dynamicConfigService,
      enabledAgentPattern,
      disabledAgentsConfig,
      500,
      null,
      runnableExecutor,
      runnableScheduler,
      shardingFilter,
      rebalanceThreshold,
      clock
    )

    return new SchedulerTestContext(
      scheduler: scheduler,
      agent: agent,
      exec: exec,
      instr: instr,
      shardingFilter: shardingFilter,
      runnableScheduler: runnableScheduler,
      runnableExecutor: runnableExecutor,
      clock: clock
    )
  }

  private void runSchedulerCycle(SchedulerTestContext ctx) {
    ctx.runnableScheduler.runAll()
    ctx.runnableExecutor.runAll()
  }

  private void expireLockInDatabase(String agentType) {
    context.update(table("cats_agent_locks"))
      .set(field("lock_expiry"), 0L)
      .where(field("agent_name").eq(agentType))
      .execute()
  }

  // Helper class to group test context
  private static class SchedulerTestContext {
    SqlClusteredAgentScheduler scheduler
    CachingAgent agent
    MockAgentLongRunningExecution exec
    ExecutionInstrumentation instr
    MockShardingFilter shardingFilter
    ManualRunnableScheduler runnableScheduler
    ManualRunnableScheduler runnableExecutor
    MutableClock clock
  }
}
