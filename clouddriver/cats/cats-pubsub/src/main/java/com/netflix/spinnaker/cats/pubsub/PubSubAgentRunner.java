package com.netflix.spinnaker.cats.pubsub;

import com.netflix.spinnaker.cats.agent.Agent;
import com.netflix.spinnaker.cats.agent.CacheResult;
import com.netflix.spinnaker.cats.agent.CachingAgent;
import com.netflix.spinnaker.cats.agent.ExecutionInstrumentation;
import com.netflix.spinnaker.cats.cluster.NodeStatusProvider;
import com.netflix.spinnaker.cats.provider.ProviderRegistry;
import com.netflix.spinnaker.clouddriver.config.PubSubSchedulerProperties;
import com.netflix.spinnaker.kork.annotations.Alpha;
import com.netflix.spinnaker.kork.lock.LockManager;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

// Uses messagelistener interfaces from redis by default, but supports others
@Log4j2
@Component
@ConditionalOnProperty("cats.pubsub.enabled")
@Alpha
public class PubSubAgentRunner implements MessageListener {
  private static final long MINIMUM_LOCK_DURATION_MILLIS = Duration.ofMinutes(1).toMillis();

  // States a runner is allowed to claim work from.  The scheduler always transitions an agent to
  // PENDING before publishing, so anything else means another runner already claimed it (or it was
  // deleted).
  private static final Set<StateMachine.State> CLAIMABLE_STATES =
      Set.of(StateMachine.State.PENDING);

  @Autowired private ProviderRegistry providerRegistry;
  @Autowired StateMachine stateMachine;
  @Autowired MeterRegistry meterRegistry;
  @Autowired PubSubSchedulerProperties properties;
  @Autowired LockManager lockManager;

  @Autowired(required = false)
  Collection<ExecutionInstrumentation> executionInstrumentations = List.of();

  // Optional: only present when a discovery/health integration is configured.  Used to stop
  // claiming new work while this replica is disabled (e.g. draining for shutdown).
  @Autowired(required = false)
  NodeStatusProvider nodeStatusProvider;

  public void onMessage(Message message, byte[] pattern) {
    String agentType = new String(message.getBody());
    log.debug("Starting on message for agent {}", agentType);
    if (nodeStatusProvider != null && !nodeStatusProvider.isNodeEnabled()) {
      log.debug(
          "Node is not enabled (draining or out of discovery); leaving agent {} for another replica.",
          agentType);
      return;
    }
    Agent agent = providerRegistry.getAgentForProviderName(agentType);
    if (agent == null) {
      log.warn(
          "Received a message for agent {} but it is not in the provider registry.  Dropping the message - it will be retried by the scheduler if the agent reappears.",
          agentType);
      return;
    }
    StateMachine.AgentState agentState = stateMachine.getAgent(agentType);
    if (agentState == null) {
      log.warn(
          "Received a message for agent {} but there is no state row for it (was it deleted?).  Dropping the message.",
          agentType);
      return;
    }

    // Redis pub/sub broadcasts each message to EVERY subscribed replica.  Claim the agent via a
    // conditional state transition so exactly one replica executes it; everyone else drops the
    // message here.
    if (stateMachine.tryTransition(agentType, CLAIMABLE_STATES, StateMachine.State.RUNNING) == 0) {
      log.debug(
          "Agent {} was already claimed by another runner (or deleted).  Skipping.", agentType);
      return;
    }
    // The PENDING transition timestamp is when the scheduler published the message, so this is the
    // time the agent spent waiting in the queue before a runner picked it up.
    meterRegistry
        .timer("cats.pubsub.queue.latency", List.of(Tag.of("agentType", agentType)))
        .record(
            Math.max(0, System.currentTimeMillis() - agentState.getLastTransitionTime()),
            TimeUnit.MILLISECONDS);

    long lockDurationMillis = computeLockDurationMillis(agentState);
    String lockName =
        StringUtils.replaceEach(
            agent.getAgentType(),
            new String[] {"-", "/", "]", "["},
            new String[] {".", ".", ".", "."});
    StopWatch totalDuration = new StopWatch();
    AtomicInteger totalDataCached = new AtomicInteger(-1);
    long startTimeMs = System.currentTimeMillis();
    try {
      // The redis lock is a second layer of protection for the one case the state machine can't
      // see: a previous execution of this agent that ran past its timeout and was requeued by the
      // scheduler.  That older execution still holds the lock, so we drop this message and let the
      // stuck-RUNNING sweep requeue the agent again once the timeout passes.
      totalDuration.start();
      LockManager.AcquireLockResponse<Void> lockResponse =
          lockManager.acquireLock(
              lockName,
              lockDurationMillis,
              () -> {
                executionInstrumentations.forEach(i -> i.executionStarted(agent));
                executeAgent(agent, agentType, totalDataCached);
                totalDuration.stop();
              });

      // acquireLock does NOT throw on contention or redis errors - it reports through the
      // response status.  Only an ACQUIRED status means the callback actually ran.
      switch (lockResponse.getLockStatus()) {
        case ACQUIRED:
          stateMachine.markAgentCompleted(
              agent.getAgentType(), totalDuration.getTotalTimeMillis(), totalDataCached.get());
          executionInstrumentations.forEach(
              i ->
                  i.executionCompleted(agent, ExecutionInstrumentation.elapsedTimeMs(startTimeMs)));
          meterRegistry
              .timer(
                  "cats.pubsub.execution.totaltime",
                  List.of(
                      Tag.of("agentType", agentType),
                      Tag.of("state", StateMachine.State.FINISHED.name())))
              .record(totalDuration.getTotalTimeMillis(), TimeUnit.MILLISECONDS);
          break;
        case TAKEN:
          // Another execution of this agent still holds the lock (most likely a previous run that
          // exceeded its timeout and got requeued).  This is NOT a failure of the agent itself:
          // leave the state alone (RUNNING accurately describes the older in-flight execution) and
          // let the scheduler's stuck-RUNNING sweep requeue it later.
          meterRegistry
              .counter(
                  "cats.pubsub.agents.processed",
                  List.of(Tag.of("agentType", agentType), Tag.of("state", "LOCKED")))
              .increment();
          log.warn(
              "Could not acquire the execution lock for agent {} (lock duration {} ms).  A previous execution likely still holds it; the scheduler will requeue this agent once its timeout expires.",
              agentType,
              lockDurationMillis);
          break;
        default:
          // ERROR / EXPIRED - the lock system itself failed (redis trouble, lease expired
          // mid-run...).  The agent may or may not have run to completion; mark it FAILED so the
          // scheduler retries it after the error interval.
          handleExecutionFailure(agent, agentType, lockResponse.getException(), startTimeMs);
      }
    } catch (Exception e) {
      // Agent executions that throw surface here as a LockCallbackException from acquireLock.
      handleExecutionFailure(agent, agentType, e, startTimeMs);
    }
  }

  private void executeAgent(Agent agent, String agentType, AtomicInteger totalDataCached) {
    if (agent instanceof CachingAgent cacheExecutionAgent) {
      CachingAgent.CacheExecution cacheAgentExecution =
          (CachingAgent.CacheExecution) cacheExecutionAgent.getAgentExecution(providerRegistry);
      List<Tag> tags =
          List.of(
              Tag.of("agentType", agentType), Tag.of("state", StateMachine.State.FINISHED.name()));
      StopWatch timer = new StopWatch();
      timer.start();
      CacheResult cacheResult = cacheAgentExecution.executeAgentWithoutStore(agent);
      totalDataCached.set(
          cacheResult.getCacheResults().values().stream()
              .map(Collection::size)
              .reduce(0, Integer::sum));
      timer.stop();
      meterRegistry
          .timer("cats.pubsub.execution.duration", tags)
          .record(timer.getTotalTimeMillis(), TimeUnit.MILLISECONDS);
      meterRegistry.summary("cats.pubsub.execution.size", tags).record(totalDataCached.get());

      timer = new StopWatch();
      timer.start();
      cacheAgentExecution.storeAgentResult(agent, cacheResult);
      timer.stop();
      meterRegistry
          .timer("cats.pubsub.execution.storagetime", tags)
          .record(timer.getTotalTimeMillis(), TimeUnit.MILLISECONDS);
      meterRegistry.counter("cats.pubsub.agents.processed", tags).increment();
    } else {
      agent.getAgentExecution(providerRegistry).executeAgent(agent);
      // UNFORTUNATELY... unless this is a CachingAgent.CacheExecution agent, we won't
      // know how much data it processed.  There are a FEW agents that are NOT
      // CacheExecution agents at this time.
      log.debug(
          "We completed {} but since it's not a CacheExecution agent we can't track how much data it processed.",
          agent.getAgentType());
    }
  }

  private void handleExecutionFailure(
      Agent agent, String agentType, Exception cause, long startTimeMs) {
    stateMachine.changeStateUnlessMarkedForDeletion(
        agent.getAgentType(), StateMachine.State.FAILED);
    executionInstrumentations.forEach(
        i -> i.executionFailed(agent, cause, ExecutionInstrumentation.elapsedTimeMs(startTimeMs)));
    meterRegistry
        .counter(
            "cats.pubsub.agents.processed",
            List.of(
                Tag.of("agentType", agentType), Tag.of("state", StateMachine.State.FAILED.name())))
        .increment();
    log.error(
        "Agent {} failed to run due to an exception.  Setting state to FAILED; the scheduler will retry it after the error interval elapses.",
        agentType,
        cause);
  }

  /**
   * Lock long enough to cover a normal run (last duration plus a buffer) but never less than a
   * minute and never more than the configured absolute maximum. New agents carry an
   * Integer.MAX_VALUE sentinel duration, so the upper clamp is what keeps their first-run lock
   * sane.
   */
  private long computeLockDurationMillis(StateMachine.AgentState agentState) {
    long maxMillis = Duration.ofMinutes(properties.getMaxDurationForAnAgentMinutes()).toMillis();
    long bufferedMillis =
        (long) (agentState.getLastDuration() * properties.getPercentMaxOverNormalDuration());
    return Math.min(Math.max(bufferedMillis, MINIMUM_LOCK_DURATION_MILLIS), maxMillis);
  }
}
