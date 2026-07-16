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
import com.netflix.spinnaker.cats.agent.LongRunningAgentExecution
import com.netflix.spinnaker.cats.agent.CachingAgent
import com.netflix.spinnaker.cats.agent.ExecutionInstrumentation
import com.netflix.spinnaker.cats.test.MockAgentLongRunningExecution
import com.netflix.spinnaker.cats.test.MockShardingFilter
import com.netflix.spinnaker.cats.test.ManualRunnableScheduler
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import com.netflix.spinnaker.cats.cluster.NodeIdentity
import com.netflix.spinnaker.cats.cluster.NodeStatusProvider
import com.netflix.spinnaker.cats.cluster.ShardingFilter
import com.netflix.spinnaker.cats.cluster.AgentIntervalProvider

import com.netflix.spinnaker.kork.sql.test.SqlTestUtil
import org.jooq.DSLContext
import org.junit.jupiter.api.BeforeEach
import org.testcontainers.DockerClientFactory
import spock.lang.Requires
import spock.lang.Specification

@Requires({ DockerClientFactory.instance().isDockerAvailable() })
abstract class SqlClusteredAgentSchedulerSpec extends Specification {
  SqlTestUtil.TestDatabase testDatabase
  DSLContext context
  AgentIntervalProvider intervalProvider
  DynamicConfigService dynamicConfigService

  def enabledAgentPattern = ".*"
  List<String> disabledAgentsConfig = Collections.emptyList()

  abstract SqlTestUtil.TestDatabase getDatabase()

  @BeforeEach
  def setup() {
    SqlTestUtil.TestDatabase testDatabase = getDatabase()
    context = testDatabase.context
    intervalProvider = Stub(AgentIntervalProvider)
    dynamicConfigService = Stub(DynamicConfigService)

    intervalProvider.getInterval(_ as Agent) >> new AgentIntervalProvider.Interval(500L, 500L)
    dynamicConfigService.getConfig(*_) >> { args -> getConfig(args[1]) }
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
    SqlClusteredAgentScheduler scheduler
    ManualRunnableScheduler runnableScheduler
    ManualRunnableScheduler runnableExecutor
    def agentType = "someagent-0"

    def agent = Stub(CachingAgent)
    def instr = Mock(ExecutionInstrumentation)
    def exec = Spy(MockAgentLongRunningExecution)


    def nodeIdentity = Stub(NodeIdentity)
    def nodeStatusProvider = Stub(NodeStatusProvider)
    def shardingFilter = Stub(ShardingFilter)

    nodeIdentity.nodeIdentity >> "node-0"
    nodeStatusProvider.isNodeEnabled() >> true
    shardingFilter.filter(_ as Agent) >> true
    agent.getAgentType() >> agentType


    runnableScheduler = new ManualRunnableScheduler()
    runnableExecutor = new ManualRunnableScheduler()

    scheduler = new SqlClusteredAgentScheduler(context, nodeIdentity, intervalProvider, nodeStatusProvider, dynamicConfigService, enabledAgentPattern, disabledAgentsConfig, 500, null,
      runnableExecutor, runnableScheduler,
      shardingFilter, 0)


    when:
    scheduler.schedule(agent, exec, instr)
    runnableScheduler.runAll()
    runnableExecutor.runAll()
    exec.fail()
    Thread.sleep(600)
    runnableScheduler.runAll()
    runnableExecutor.runAll()

    then:
    2 * exec.executeAgent(_)
    1 * exec.stopExecutingAndCleanup()

  }

  def 'longRunningAgent isn\'t rescheduled if still running but node topology changes'() {
    given:
    SqlClusteredAgentScheduler scheduler1
    ManualRunnableScheduler runnableScheduler1
    ManualRunnableScheduler runnableExecutor1
    SqlClusteredAgentScheduler scheduler2
    ManualRunnableScheduler runnableScheduler2
    ManualRunnableScheduler runnableExecutor2
    def agentType = "someagent-1"

    // node 1
    def agent1 = Stub(CachingAgent)
    def instr1 = Mock(ExecutionInstrumentation)
    def exec1 = Spy(MockAgentLongRunningExecution)
    def nodeIdentity1 = Stub(NodeIdentity)
    def nodeStatusProvider1 = Stub(NodeStatusProvider)
    def shardingFilter1 = new MockShardingFilter()

    nodeIdentity1.nodeIdentity >> "node1"
    nodeStatusProvider1.isNodeEnabled() >> true
    agent1.getAgentType() >> agentType

    runnableScheduler1 = new ManualRunnableScheduler()
    runnableExecutor1 = new ManualRunnableScheduler()

    scheduler1 = new SqlClusteredAgentScheduler(context, nodeIdentity1, intervalProvider, nodeStatusProvider1, dynamicConfigService, enabledAgentPattern, disabledAgentsConfig, 500, null,
      runnableExecutor1, runnableScheduler1,
      shardingFilter1, 0)

    // node 2
    def agent2 = Stub(CachingAgent)
    def instr2 = Mock(ExecutionInstrumentation)
    def exec2 = Mock(LongRunningAgentExecution)
    def nodeIdentity2 = Stub(NodeIdentity)
    def nodeStatusProvider2 = Stub(NodeStatusProvider)
    def shardingFilter2 = new MockShardingFilter()

    nodeIdentity2.nodeIdentity >> "node2"
    nodeStatusProvider2.isNodeEnabled() >> true
    shardingFilter2.filter(_ as Agent) >> true
    agent2.getAgentType() >> agentType


    runnableScheduler2 = new ManualRunnableScheduler()
    runnableExecutor2 = new ManualRunnableScheduler()

    scheduler2 = new SqlClusteredAgentScheduler(context, nodeIdentity2, intervalProvider, nodeStatusProvider2, dynamicConfigService, enabledAgentPattern, disabledAgentsConfig, 500, null,
      runnableExecutor2, runnableScheduler2,
      shardingFilter2, 0)


    when:
    shardingFilter1.add("someagent-1")
    scheduler1.schedule(agent1, exec1, instr1)
    runnableScheduler1.runAll()
    runnableExecutor1.runAll()
    Thread.sleep(600)
    exec1.fail()
    shardingFilter1.remove("someagent-1")
    runnableScheduler1.runAll()
    runnableExecutor1.runAll()
    scheduler2.schedule(agent2, exec2, instr2)
    runnableScheduler2.runAll()
    runnableExecutor2.runAll()

    then:
    1 * exec1.executeAgent(_)
    1 * exec1.stopExecutingAndCleanup()
    0 * exec2.executeAgent(_)
  }

  def 'longRunningAgent is rescheduled if stopped and node topology changes'() {
    given:
    SqlClusteredAgentScheduler scheduler1
    ManualRunnableScheduler runnableScheduler1
    ManualRunnableScheduler runnableExecutor1
    SqlClusteredAgentScheduler scheduler2
    ManualRunnableScheduler runnableScheduler2
    ManualRunnableScheduler runnableExecutor2
    def agentType = "someagent-3"

    // node 1
    def agent1 = Stub(CachingAgent)
    def instr1 = Mock(ExecutionInstrumentation)
    def exec1 = Spy(MockAgentLongRunningExecution)
    def nodeIdentity1 = Stub(NodeIdentity)
    def nodeStatusProvider1 = Stub(NodeStatusProvider)
    def shardingFilter1 = new MockShardingFilter()

    nodeIdentity1.nodeIdentity >> "node1"
    nodeStatusProvider1.isNodeEnabled() >> true
    shardingFilter1.add(agentType)
    agent1.getAgentType() >> agentType

    runnableScheduler1 = new ManualRunnableScheduler()
    runnableExecutor1 = new ManualRunnableScheduler()

    scheduler1 = new SqlClusteredAgentScheduler(context, nodeIdentity1, intervalProvider, nodeStatusProvider1, dynamicConfigService, enabledAgentPattern, disabledAgentsConfig, 500, null,
      runnableExecutor1, runnableScheduler1,
      shardingFilter1, 0)

    // node 2
    def agent2 = Stub(CachingAgent)
    def instr2 = Mock(ExecutionInstrumentation)
    def exec2 = Spy(MockAgentLongRunningExecution)
    def nodeIdentity2 = Stub(NodeIdentity)
    def nodeStatusProvider2 = Stub(NodeStatusProvider)
    def shardingFilter2 = new MockShardingFilter()

    nodeIdentity2.nodeIdentity >> "node2"
    nodeStatusProvider2.isNodeEnabled() >> true
    agent2.getAgentType() >> agentType


    runnableScheduler2 = new ManualRunnableScheduler()
    runnableExecutor2 = new ManualRunnableScheduler()

    scheduler2 = new SqlClusteredAgentScheduler(context, nodeIdentity2, intervalProvider, nodeStatusProvider2, dynamicConfigService, enabledAgentPattern, disabledAgentsConfig, 500, null,
      runnableExecutor2, runnableScheduler2,
      shardingFilter2, 0)


    when:
    scheduler1.schedule(agent1, exec1, instr1)
    runnableScheduler1.runAll()
    runnableExecutor1.runAll()

    Thread.sleep(600)

    shardingFilter1.remove(agentType)
    shardingFilter2.add(agentType)
    exec1.fail()
    runnableScheduler1.runAll()
    runnableExecutor1.runAll()

    scheduler2.schedule(agent2, exec2, instr2)
    runnableScheduler2.runAll()
    runnableExecutor2.runAll()

    //guarantee it is not stopped twice
    runnableScheduler1.runAll()
    runnableExecutor1.runAll()

    then:
    1 * exec1.executeAgent(_)
    1 * exec1.stopExecutingAndCleanup()
    1 * exec2.executeAgent(_)
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

    scheduler1 = new SqlClusteredAgentScheduler(context, nodeIdentity1, intervalProvider, nodeStatusProvider1, dynamicConfigService, enabledAgentPattern, disabledAgentsConfig, 500, null,
      runnableExecutor1, runnableScheduler1,
      shardingFilter1, 50)


    when:
    scheduler1.schedule(agent1, exec1, instr1)
    scheduler1.schedule(agent2, exec2, instr2)
    runnableScheduler1.runAll()
    runnableExecutor1.runAll()

    Thread.sleep(600)

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

    scheduler1 = new SqlClusteredAgentScheduler(context, nodeIdentity1, intervalProvider, nodeStatusProvider1, dynamicConfigService, enabledAgentPattern, disabledAgentsConfig, 500, null,
      runnableExecutor1, runnableScheduler1,
      shardingFilter1, 300)


    when:
    scheduler1.schedule(agent1, exec1, instr1)
    scheduler1.schedule(agent2, exec2, instr2)
    runnableScheduler1.runAll()
    runnableExecutor1.runAll()

    Thread.sleep(600)

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

    scheduler1 = new SqlClusteredAgentScheduler(context, nodeIdentity1, intervalProvider, nodeStatusProvider1, dynamicConfigService, enabledAgentPattern, disabledAgentsConfig, 500, null,
      runnableExecutor1, runnableScheduler1,
      shardingFilter1, 50)


    when:
    scheduler1.schedule(agent1, exec1, instr1)
    scheduler1.schedule(agent2, exec2, instr2)
    runnableScheduler1.runAll()
    runnableExecutor1.runAll()

    Thread.sleep(600)

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

    scheduler1 = new SqlClusteredAgentScheduler(context, nodeIdentity1, intervalProvider, nodeStatusProvider1, dynamicConfigService, enabledAgentPattern, disabledAgentsConfig, 500, null,
      runnableExecutor1, runnableScheduler1,
      shardingFilter1, 0)

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

    scheduler2 = new SqlClusteredAgentScheduler(context, nodeIdentity2, intervalProvider, nodeStatusProvider2, dynamicConfigService, enabledAgentPattern, disabledAgentsConfig, 500, null,
      runnableExecutor2, runnableScheduler2,
      shardingFilter2, 0)


    when:
    scheduler1.schedule(agent1, exec1, instr1)
    runnableScheduler1.runAll()
    runnableExecutor1.runAll()

    Thread.sleep(600)

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

    scheduler1 = new SqlClusteredAgentScheduler(context, nodeIdentity1, biggerIntervalProvider, nodeStatusProvider1, dynamicConfigService, enabledAgentPattern, disabledAgentsConfig, 500, null,
      runnableExecutor1, runnableScheduler1,
      shardingFilter1, 0)

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

    scheduler2 = new SqlClusteredAgentScheduler(context, nodeIdentity2, biggerIntervalProvider, nodeStatusProvider2, dynamicConfigService, enabledAgentPattern, disabledAgentsConfig, 500, null,
      runnableExecutor2, runnableScheduler2,
      shardingFilter2, 0)


    when:
    scheduler1.schedule(agent1, exec1, instr1)
    runnableScheduler1.runAll()
    runnableExecutor1.runAll()

    Thread.sleep(200)

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

}
