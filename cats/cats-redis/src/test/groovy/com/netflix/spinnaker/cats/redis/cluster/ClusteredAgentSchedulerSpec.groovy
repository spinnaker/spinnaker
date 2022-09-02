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

package com.netflix.spinnaker.cats.redis.cluster

import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.netflix.spinnaker.cats.agent.Agent
import com.netflix.spinnaker.cats.agent.AgentExecution
import com.netflix.spinnaker.cats.agent.CachingAgent
import com.netflix.spinnaker.cats.agent.ExecutionInstrumentation
import com.netflix.spinnaker.cats.cluster.DefaultAgentIntervalProvider
import com.netflix.spinnaker.cats.cluster.DefaultNodeIdentity
import com.netflix.spinnaker.cats.cluster.DefaultNodeStatusProvider
import com.netflix.spinnaker.cats.cluster.NoopShardingFilter
import com.netflix.spinnaker.cats.test.ManualRunnableScheduler
import com.netflix.spinnaker.cats.test.TestAgent
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import com.netflix.spinnaker.kork.jedis.JedisClientDelegate
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPool
import redis.clients.jedis.params.SetParams
import spock.lang.Specification
import spock.lang.Subject
import java.util.concurrent.Executors

class ClusteredAgentSchedulerSpec extends Specification {

    @Subject
    ClusteredAgentScheduler scheduler

    Jedis jedis
    JedisPool jedisPool
    CachingAgent agent
    ManualRunnableScheduler lockPollingScheduler
    ManualRunnableScheduler agentExecutionScheduler
    AgentExecution exec = Mock(AgentExecution)
    ExecutionInstrumentation inst = Mock(ExecutionInstrumentation)
    DynamicConfigService dcs = Stub(DynamicConfigService) {
      getConfig(Integer, _ as String, 1000) >> 1000
    }

    def setup() {
        def interval = new DefaultAgentIntervalProvider(6000000)
        agent = new TestAgent()
        jedis = Mock(Jedis)
        jedisPool = Stub(JedisPool) {
            getResource() >> jedis
        }
        lockPollingScheduler = new ManualRunnableScheduler()
        agentExecutionScheduler = new ManualRunnableScheduler()
        scheduler = new ClusteredAgentScheduler(
          new JedisClientDelegate(jedisPool),
          new DefaultNodeIdentity(),
          interval,
          new DefaultNodeStatusProvider(),
          lockPollingScheduler,
          agentExecutionScheduler,
          ".*",
          null,
          dcs,
          new NoopShardingFilter()
        )
    }

    def 'cache run aborted if agent doesnt acquire execution token'() {

        when:
        scheduler.schedule(agent, exec, inst)
        lockPollingScheduler.runAll()
        agentExecutionScheduler.runAll()

        then:
        1 * jedis.set(_ as String, _ as String, _ as SetParams) >> 'definitely not ok'
        1 * jedis.close()
        0 * _
    }

    def 'cache run proceeds if agent acquires execution token'() {
        when:
        scheduler.schedule(agent, exec, inst)
        lockPollingScheduler.runAll()
        agentExecutionScheduler.runAll()

        then:
        1 * jedis.set(_ as String, _ as String, _ as SetParams) >> 'OK'
        1 * inst.executionStarted(agent)
        1 * exec.executeAgent(agent)
        1 * inst.executionCompleted(agent, _)
        1 * jedis.eval(_ as String, _ as List, _ as List)
        2 * jedis.close()
        0 * _
    }

    def 'execution token is TTLd when agent execution fails'() {
        setup:
        Throwable cause = new RuntimeException("fail")

        when:
        scheduler.schedule(agent, exec, inst)
        lockPollingScheduler.runAll()
        agentExecutionScheduler.runAll()

        then:
        1 * jedis.set(_ as String, _ as String, _ as SetParams) >> 'OK'
        1 * inst.executionStarted(agent)
        1 * exec.executeAgent(agent) >> { throw cause }
        1 * inst.executionFailed(agent, cause, _)
        1 * jedis.eval(_ as String, _ as List, _ as List)
        2 * jedis.close()
        0 * _
    }

    def 'test agent addition and removal from the agents and activeAgents maps in the schedule() -> run -> unschedule() flow'() {
      when:
      scheduler.schedule(agent, exec, inst)
      then:
      // scheduling an agent should add it to the agents map
      scheduler.agents.containsKey(agent.agentType)
      // unless we run this agent, it won't show up in the active agents map
      !scheduler.activeAgents.containsKey(agent.agentType)

      when:
      lockPollingScheduler.runAll()
      agentExecutionScheduler.runAll()

      then:
      // after running the agent, agents map should still contain it as we haven't explicitly
      // removed it
      scheduler.agents.containsKey(agent.agentType)
      1 * jedis.set(_ as String, _ as String, _ as SetParams) >> 'OK'
      1 * inst.executionStarted(agent)
      1 * exec.executeAgent(agent)
      1 * inst.executionCompleted(agent, _)

      // normal execution of the agent will call agentCompleted() which calls releaseRunKey() which makes
      // the following jedis call, and then it removes it from the activeAgents map
      1 * jedis.eval(scheduler.TTL_LOCK_KEY, List.of(agent.agentType), _ as List)
      !scheduler.activeAgents.containsKey(agent.agentType)

      2 * jedis.close()
      0 * _

      when:
      scheduler.unschedule(agent)

      then:
      // unschedule() should make this following jedis call
      1 * jedis.eval(scheduler.DELETE_LOCK_KEY, List.of(agent.agentType), _ as List)

      // unschedule() should remove the agent from both agents and activeAgents map
      !scheduler.agents.containsKey(agent.agentType)
      // in the context of this test, the agent was already removed from active agents before unschedule()
      // was called
      !scheduler.activeAgents.containsKey(agent.agentType)
    }

  def 'test that a long-running/stuck agent is removed from the active agents map after sufficient time has elapsed'() {
    given:
    def arbitraryAgentInterval = 500l
    // agent is configured with an interval of 500ms (so this agent is supposed to timeout after 2 * 500 = 1s)
    agent = new TestAgent(arbitraryAgentInterval)

    def agentExecutionScheduler = Executors.newCachedThreadPool(
      new ThreadFactoryBuilder()
        .setNameFormat(TestStuckAgentExecution.class.getSimpleName() + "-%d")
        .build())

    // this interval is only used if an agent doesn't provide its own interval... this value is not
    // used in the tests since our test agent implements AgentIntervalAware
    def interval = new DefaultAgentIntervalProvider(1)

    def realScheduler = new ClusteredAgentScheduler(
      new JedisClientDelegate(jedisPool),
      new DefaultNodeIdentity(),
      interval,
      new DefaultNodeStatusProvider(),
      lockPollingScheduler,
      agentExecutionScheduler,
      ".*",
      null,
      dcs,
      new NoopShardingFilter()
    )

    // sleep for 5s
    def agentExecution = new TestStuckAgentExecution(10*arbitraryAgentInterval)

    when:
    realScheduler.schedule(agent, agentExecution, inst)

    then:
    // scheduling an agent should add it to the agents map
    realScheduler.agents.containsKey(agent.agentType)
    // unless we run this agent, it won't show up in the active agents map
    !realScheduler.activeAgents.containsKey(agent.agentType)

    when:
    // run the agent
    lockPollingScheduler.runAll()

    then:
    // since this is a long running agent execution, after running it, the agents map should still contain it as
    // it hasn't completed just yet
    realScheduler.agents.containsKey(agent.agentType)
    1 * jedis.set(_ as String, _ as String, _ as SetParams) >> 'OK'

    // verify that the agent hasn't completed its work
    // normal execution of the agent will call agentCompleted() which calls releaseRunKey() which makes
    // the following jedis call, and then it removes it from the activeAgents map. But since it hasn't
    // completed its work, the above wouldn't be true
    0 * jedis.eval(realScheduler.TTL_LOCK_KEY, List.of(agent.agentType), _ as List)
    // it should still be in the active agents map
    realScheduler.activeAgents.containsKey(agent.agentType)

    1 * jedis.close()

    when:
    // arbitrary sleep interval > agent timeout
    Thread.sleep(4 * arbitraryAgentInterval)
    lockPollingScheduler.runAll()

    then:
    // since enough time has elapsed, it should be removed from the active agents map.
    !realScheduler.activeAgents.containsKey(agent.agentType)

    // verify that ttl lock key hasn't been updated just yet
    // (i.e. it was automatically removed from active agents map)
    0 * jedis.eval(realScheduler.TTL_LOCK_KEY, List.of(agent.agentType), _ as List)
  }

    def 'test that an agent is not removed from the active agents map if sufficient time has not elapsed'() {
      given:
      def arbitraryAgentInterval = 500l
      // agent is configured with an interval of 500ms (so this agent is supposed to timeout after 2 * 500 = 1s)
      agent = new TestAgent(arbitraryAgentInterval)

      def agentExecutionScheduler = Executors.newCachedThreadPool(
        new ThreadFactoryBuilder()
          .setNameFormat(TestStuckAgentExecution.class.getSimpleName() + "-%d")
          .build())

      // this interval is only used if an agent doesn't provide its own interval... this value is not
      // used in the tests since our test agent implements AgentIntervalAware
      def interval = new DefaultAgentIntervalProvider(1)

      def realScheduler = new ClusteredAgentScheduler(
        new JedisClientDelegate(jedisPool),
        new DefaultNodeIdentity(),
        interval,
        new DefaultNodeStatusProvider(),
        lockPollingScheduler,
        agentExecutionScheduler,
        ".*",
        null,
        dcs,
        new NoopShardingFilter()
      )

      // sleep for 5s
      def agentExecution = new TestStuckAgentExecution(10*arbitraryAgentInterval)
      when:
      realScheduler.schedule(agent, agentExecution, inst)

      then:
      // scheduling an agent should add it to the agents map
      realScheduler.agents.containsKey(agent.agentType)
      // unless we run this agent, it won't show up in the active agents map
      !realScheduler.activeAgents.containsKey(agent.agentType)

      when:
      // run the agent
      lockPollingScheduler.runAll()

      then:
      // after running the agent, agents map should still contain it as we haven't explicitly
      // removed it
      realScheduler.agents.containsKey(agent.agentType)
      1 * jedis.set(_ as String, _ as String, _ as SetParams) >> 'OK'

      // verify that the agent hasn't completed its work
      // normal execution of the agent will call agentCompleted() which calls releaseRunKey() which makes
      // the following jedis call, and then it removes it from the activeAgents map. But since it hasn't
      // completed its work, the above wouldn't be true
      0 * jedis.eval(realScheduler.TTL_LOCK_KEY, List.of(agent.agentType), _ as List)
      // it should still be in the active agents map
      realScheduler.activeAgents.containsKey(agent.agentType)

      1 * jedis.close()

      when:
      // initiate the next attempt to schedule new agents
      lockPollingScheduler.runAll()

      then:
      // since the agent is long running, and enough time hasn't elapsed, it will still be in the active agents map
      realScheduler.activeAgents.containsKey(agent.agentType)
    }

    /**
     * a test {@link AgentExecution} class that simulates a long-running/stuck agent execution
     */
    private class TestStuckAgentExecution implements AgentExecution {
      private long sleepTime

      TestStuckAgentExecution(long sleepTime) {
        this.sleepTime = sleepTime
      }

      @Override
      void executeAgent(Agent agent) {
        try {
          // an arbitrary long enough sleep value
          Thread.sleep(sleepTime)
        } catch(Exception ignored) {
          // ignore
        }
      }
    }
}
