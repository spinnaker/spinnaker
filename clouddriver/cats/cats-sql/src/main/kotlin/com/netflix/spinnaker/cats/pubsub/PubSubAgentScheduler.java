package com.netflix.spinnaker.cats.pubsub;

import com.netflix.spinnaker.cats.agent.Agent;
import com.netflix.spinnaker.cats.agent.AgentExecution;
import com.netflix.spinnaker.cats.agent.AgentLock;
import com.netflix.spinnaker.cats.agent.AgentScheduler;
import com.netflix.spinnaker.cats.agent.AgentSchedulerAware;
import com.netflix.spinnaker.cats.agent.ExecutionInstrumentation;
import com.netflix.spinnaker.cats.cluster.AgentIntervalProvider;
import com.netflix.spinnaker.cats.module.CatsModuleAware;
import com.netflix.spinnaker.cats.provider.ProviderRegistry;
import com.netflix.spinnaker.clouddriver.cache.OnDemandAgent;
import com.netflix.spinnaker.clouddriver.cache.OnDemandCacheResult;
import com.netflix.spinnaker.clouddriver.cache.OnDemandCacheUpdater;
import com.netflix.spinnaker.clouddriver.cache.OnDemandType;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * This is an implementation of a scheduler system for spinnaker that operates with a slightly DIFFERENT approach.
 * <p>
 * There two parts
 * * A state management machine that tracks known agents and their current processing state
 * * A runner system that actually processes agents based upon the state machine
 * <p>
 * This implementation uses a pub/sub based approach where the state machine publishes messages to a redis
 * queue (by default).  The runners are VERY simple "pull" from said queue & run the given agent.
 */

@Component
@ConditionalOnProperty("cats.pubsub.enabled")
@Log4j2
public class PubSubAgentScheduler extends CatsModuleAware implements Runnable, AgentScheduler<AgentLock>, OnDemandCacheUpdater {

  public static final List<StateMachine.State> QUEABLE_STATE_LIST = List.of(StateMachine.State.FINISHED, StateMachine.State.NOT_STARTED, StateMachine.State.FAILED);
  private StateMachine stateMachine;
  private final AgentIntervalProvider intervalProvider;
  public ProviderRegistry providerRegistry;
  ThreadPoolExecutor executor;

  @Autowired
  PubSubAgentScheduler(ProviderRegistry providerRegistry, StateMachine stateMachine, AgentIntervalProvider intervalProvider) {
    this.providerRegistry = providerRegistry;
    this.stateMachine = stateMachine;
    this.intervalProvider = intervalProvider;
    Runtime.getRuntime().addShutdownHook(new Thread(() -> this.shutdown()));
    executor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
    executor.execute(this);
  }


  /**
   * Overview
   * - there are three entry points to "schedule" an agent for cache operations under normal flows.  We'll ignore "OnDemand" for the moment.
   * 1) BaseProvider - which ALL providers should extend.  This on "addAgent" will invoke "scheduler.schedule" on new agents.
   * 2) AgentController - which looks like it's generated as part of the DefaultCatsModule - which is invoked to setup
   * agents for processing.  INITIALLY.
   * <p>
   * IT LOOKS like the whole point of the "CatsModule" is ONE place in "BaseProvider" to allow injection via:
   * catsModule.getProviderRegistry().  This seems like a missing piece since Autowiring handles more of htis
   * AND could have used this to provide the providerregistry vs. shoving this all into a "CatsModule".  This seems
   * like a disagreement between a guava "Module" approach vs. "Spring DI" type style of injecting required components.
   * <p>
   * Ignoring history - this is key for the baseProvider.  As this is how NEW agents get added to the list
   * to be processed
   */
  @Override
  public void schedule(Agent agent, AgentExecution agentExecution, ExecutionInstrumentation executionInstrumentation) {
    stateMachine.scheduleAgent(agent);
  }

  // LIke the schedule - this should update state machine to remove an agent IF it's not already running.
  // and if it IS running/pending, still delete it just realize it'll stil be running SOME data until fully deleted

  @Override
  public void unschedule(Agent agent) {
    stateMachine.remove(agent);
  }

  @Override
  public boolean isAtomic() {
    return true;
  }

  /* ******************************




  // ON DEMAND SECTION - EVERYTHING below this is tied to ON DEMAND behavior!

  We always own the lock :)  The state system is fully transactional and verified.  PLUS... operation/agent
  execution is handled via pub/sub consumer :)
   */
// used in ondemand to say "OK need to run an on demand agent,"
  @Override
  public AgentLock tryLock(Agent agent) {
    return new AgentLock(agent);
  }

  @Override
  public boolean tryRelease(AgentLock lock) {
    return true;
  }

  @Override
  public boolean lockValid(AgentLock lock) {
    return true;
  }

  // Pulled from @CatsOnDemandCacheUpdater
  private Collection<OnDemandAgent> getOnDemandAgents() {
    return providerRegistry.getProviders().stream()
      .flatMap(
        provider -> provider.getAgents().stream().filter(it -> it instanceof OnDemandAgent))
      .map(it -> (OnDemandAgent) it)
      .collect(Collectors.toList());
  }


  // Pulled from @CatsOnDemandCacheUpdater
  @Override
  public boolean handles(OnDemandType type, String cloudProvider) {
    return getOnDemandAgents().stream().anyMatch(it -> it.handles(type, cloudProvider));
  }

  @Override
  public OnDemandCacheResult handle(OnDemandType type, String cloudProvider, Map<String, ?> data) {
    return null;
  }

  @Override
  public Collection<Map<String, Object>> pendingOnDemandRequests(OnDemandType type, String cloudProvider) {
    return List.of();
  }

  @Override
  public Map<String, Object> pendingOnDemandRequest(OnDemandType type, String cloudProvider, String id) {
    return Map.of();
  }

  @Override
  public void run() {

    // Schedule & queue any agents that CAN be scheduled.
    stateMachine.listAgents().stream().filter(
      each -> QUEABLE_STATE_LIST.contains(each.getCurrentState())
    ).forEach(agent -> {
      Agent agentThatCouldBeRun = providerRegistry.getAgentForProviderName(agent.getAgentType());
      AgentIntervalProvider.Interval interval = intervalProvider.getInterval(agentThatCouldBeRun);
      if (agent.getLastExecutionTime() <= System.currentTimeMillis() - interval.getInterval() ) {
        //schedule the agent to run.
        try {
          stateMachine.storeAgentState(agentThatCouldBeRun, StateMachine.State.PENDING);
          queue.enqueue(agent.getAgentType());
        } catch (Exception e) {
         log.error("Failed to schedule agent {}.  This updates the table to mark that it is in a pending state aka queued.  It's LIKELY this will have as a result NOT processed the agent at all!", agentThatCouldBeRun.getAgentType(), e);
        }
      }
    });
    // Find any agents that are in a PENDING state LONGER than the execution max OR the "reschedule interval max" and reschedule them.  There's a caution if they're rescheduled TOO many times... we may have lots in the queue AND concurrently processing them on top
    // however, the lock system SHOULD reject those into a FAILED state when it encounters them as part of the lock oeprations.
    stateMachine.listAgents().stream().filter(each -> each.getCurrentState() == StateMachine.State.RUNNING).forEach(runningAgent -> {
      AgentIntervalProvider.Interval interval = intervalProvider.getInterval(providerRegistry.getAgentForProviderName(runningAgent.getAgentType()));
      if (interval.getTimeout() <= System.currentTimeMillis() - runningAgent.getLastTransitionTime()) {
        // The agent has been running TOO long.  This is LIKELY a bad situation.  We'll reschedule it and mark it for pending status. UNFORTUNATELY there's no "interrupt" option on Agents in spinnaker to DO This :( NOT Yet :(  SO we just reschedule the execution instead.
        // The bad side is you CAN get multiple agents of the same type running. E.g. if you have a Lambda caching agent that takes 5 hours to RUN... well, you may have 3 on that same account/region due to the max interval where it'll requeue that agent for processing.
        stateMachine.storeAgentState(agentThatCouldBeRun, StateMachine.State.PENDING);
        queue.enqueue(runningAgent.getAgentType());
      }
    });
    stateMachine.listAgents().stream().filter(each -> each.getCurrentState() == StateMachine.State.DELETED).forEach(runningAgent -> {
      // Remove any agent marked as DELETED which hasn't changed in over 3 hours.
      if (runningAgent.getLastTransitionTime() > System.currentTimeMillis() - 3*60*60*1000) {
        // The agent has been running TOO long.  This is LIKELY a bad situation.  Figure out which pod it is, kill the agent process, and move on.  UNFORTUNATELY there's no "interrupt" option on Agents in spinnaker to DO This :( NOT Yet :(
        stateMachine.delete(runningAgent);
      }
    });
    //reschedule pending agents every 15 seconds.
    try {
      Thread.sleep(15000);
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  private void shutdown() {
    executor.shutdown();
  }
}
