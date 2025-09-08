package com.netflix.spinnaker.cats.pubsub;

import com.netflix.spinnaker.cats.agent.Agent;
import com.netflix.spinnaker.cats.agent.AgentExecution;
import com.netflix.spinnaker.cats.agent.AgentLock;
import com.netflix.spinnaker.cats.agent.AgentScheduler;
import com.netflix.spinnaker.cats.agent.ExecutionInstrumentation;
import com.netflix.spinnaker.cats.cluster.AgentIntervalProvider;
import com.netflix.spinnaker.cats.module.CatsModuleAware;
import com.netflix.spinnaker.cats.provider.ProviderRegistry;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

/**
 * This is an implementation of a scheduler system for spinnaker that operates with a slightly
 * DIFFERENT approach.
 *
 * <p>There are three key compoentns to this scheduler: 1) A state management machine that tracks
 * known agents and their current processing state. This knows ALL known agents and their state. It
 * also tracks the last time an agent was run, and the last time it was scheduled to run. 3) A
 * poller that looks up the state and queues agents to be run based upon state/status 4) A message
 * handler that given an agent identifed by "agentType", will actually do the agent execution. This
 * will ALSO update the state.
 *
 * <p>This implementation uses a pub/sub based approach where the state machine publishes messages
 * to a redis queue (by default). The runners are VERY simple "pull" from said queue & run the given
 * agent.
 */
@Component
@ConditionalOnProperty("cats.pubsub.enabled")
@Log4j2
public class PubSubAgentScheduler extends CatsModuleAware
    implements Runnable, AgentScheduler<AgentLock> {

  public static final Set<StateMachine.State> QUEABLE_STATE_LIST =
      Set.of(
          StateMachine.State.FINISHED, StateMachine.State.NOT_STARTED, StateMachine.State.FAILED);
  public static final String CHANNEL = "pubsbub_agent_queue";
  private StateMachine stateMachine;
  private final AgentIntervalProvider intervalProvider;
  public ProviderRegistry providerRegistry;
  RedisTemplate<String, String> redisTemplate;
  ThreadPoolExecutor executor;

  @Autowired
  PubSubAgentScheduler(
      ProviderRegistry providerRegistry,
      StateMachine stateMachine,
      AgentIntervalProvider intervalProvider,
      RedisTemplate<String, String> redisTemplate) {
    this.providerRegistry = providerRegistry;
    this.stateMachine = stateMachine;
    this.intervalProvider = intervalProvider;
    this.redisTemplate = redisTemplate;
    Runtime.getRuntime().addShutdownHook(new Thread(() -> this.shutdown()));
    executor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
    executor.execute(this);
  }

  /**
   * Overview - there are three entry points to "schedule" an agent for cache operations under
   * normal flows. We'll ignore "OnDemand" for the moment. 1) BaseProvider - which ALL providers
   * should extend. This on "addAgent" will invoke "scheduler.schedule" on new agents. 2)
   * AgentController - which looks like it's generated as part of the DefaultCatsModule - which is
   * invoked to setup agents for processing. INITIALLY.
   *
   * <p>IT LOOKS like the whole point of the "CatsModule" is ONE place in "BaseProvider" to allow
   * injection via: catsModule.getProviderRegistry(). This seems like a missing piece since
   * Autowiring handles more of htis AND could have used this to provide the providerregistry vs.
   * shoving this all into a "CatsModule". This seems like a disagreement between a guava "Module"
   * approach vs. "Spring DI" type style of injecting required components.
   *
   * <p>Ignoring history - this is key for the baseProvider. As this is how NEW agents get added to
   * the list to be processed
   */
  @Override
  public void schedule(
      Agent agent,
      AgentExecution agentExecution,
      ExecutionInstrumentation executionInstrumentation) {
    try {
      stateMachine.createOrUpdateAgent(agent, StateMachine.State.NOT_STARTED);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  // LIke the schedule - this should update state machine to remove an agent IF it's not already
  // running.
  // and if it IS running/pending, still delete it just realize it'll stil be running SOME data
  // until fully deleted

  @Override
  public void unschedule(Agent agent) {
    stateMachine.disable(agent);
  }
  /* ******************************
  // ON DEMAND SECTION - EVERYTHING below this is tied to ON DEMAND behavior!

  We always own the lock :)  The state system is fully transactional and verified.  PLUS... operation/agent
  execution is handled via pub/sub consumer :)
   */
  // used in ondemand to say "OK need to run an on demand agent,"

  @Override
  public void run() {

    // Schedule & queue any agents that CAN be scheduled.
    stateMachine
        .listAgentsFilteredWhereIn(QUEABLE_STATE_LIST)
        .forEach(
            agent -> {
              Agent agentThatCouldBeRun =
                  providerRegistry.getAgentForProviderName(agent.getAgentType());
              AgentIntervalProvider.Interval interval =
                  intervalProvider.getInterval(agentThatCouldBeRun);
              if (agent.getLastExecutionTime()
                  <= System.currentTimeMillis() - interval.getInterval()) {
                // schedule the agent to run.
                try {
                  stateMachine.createOrUpdateAgent(agentThatCouldBeRun, StateMachine.State.PENDING);
                  redisTemplate.convertAndSend(CHANNEL, agent.getAgentType());
                } catch (Exception e) {
                  log.error(
                      "Failed to schedule agent {}.  This updates the table to mark that it is in a pending state aka queued.  It's LIKELY this will have as a result NOT processed the agent at all!",
                      agentThatCouldBeRun.getAgentType(),
                      e);
                }
              }
            });
    // Find any agents that are in a PENDING state LONGER than the execution max OR the "reschedule
    // interval max" and reschedule them.  There's a caution if they're rescheduled TOO many
    // times... we may have lots in the queue AND concurrently processing them on top
    // however, the lock system SHOULD reject those into a FAILED state when it encounters them as
    // part of the lock oeprations.
    stateMachine
        .listAgentsFilteredWhereIn(Set.of(StateMachine.State.RUNNING))
        .forEach(
            runningAgent -> {
              Agent agentThatCouldBeRun =
                  providerRegistry.getAgentForProviderName(runningAgent.getAgentType());
              AgentIntervalProvider.Interval interval =
                  intervalProvider.getInterval(
                      providerRegistry.getAgentForProviderName(runningAgent.getAgentType()));
              if (interval.getTimeout()
                  <= System.currentTimeMillis() - runningAgent.getLastTransitionTime()) {
                // The agent has been running TOO long.  This is LIKELY a bad situation.  We'll
                // reschedule it and mark it for pending status. UNFORTUNATELY there's no
                // "interrupt" option on Agents in spinnaker to DO This :( NOT Yet :(  SO we just
                // reschedule the execution instead.
                // The bad side is you CAN get multiple agents of the same type running. E.g. if you
                // have a Lambda caching agent that takes 5 hours to RUN... well, you may have 3 on
                // that same account/region due to the max interval where it'll requeue that agent
                // for processing.
                try {
                  // ONLY send to the queue IF we successfully moved it to a pending state.  This
                  // skips over a record marked for deletion.  TECHNICALLY this shouldn't happen.
                  if (stateMachine.changeStateUnlessMarkedForDeletion(
                          agentThatCouldBeRun, StateMachine.State.PENDING)
                      >= 0) {
                    redisTemplate.convertAndSend(CHANNEL, runningAgent.getAgentType());
                  }
                } catch (Exception e) {
                  throw new RuntimeException(e);
                }
              }
            });
    stateMachine
        .listAgentsFilteredWhereIn(Set.of(StateMachine.State.DELETED))
        .forEach(
            runningAgent -> {
              // Remove any agent marked as DELETED which hasn't changed in over 3 hours.
              if (runningAgent.getLastTransitionTime()
                  > System.currentTimeMillis() - 3 * 60 * 60 * 1000) {
                // The agent has been running TOO long.  This is LIKELY a bad situation.  Figure out
                // which pod it is, kill the agent process, and move on.  UNFORTUNATELY there's no
                // "interrupt" option on Agents in spinnaker to DO This :( NOT Yet :(
                stateMachine.delete(runningAgent.getAgentType());
              }
            });
    // reschedule pending agents every 15 seconds.
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
