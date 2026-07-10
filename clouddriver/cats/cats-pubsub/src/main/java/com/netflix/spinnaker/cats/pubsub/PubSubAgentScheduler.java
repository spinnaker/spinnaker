package com.netflix.spinnaker.cats.pubsub;

import com.netflix.spinnaker.cats.agent.Agent;
import com.netflix.spinnaker.cats.agent.AgentExecution;
import com.netflix.spinnaker.cats.agent.AgentLock;
import com.netflix.spinnaker.cats.agent.AgentScheduler;
import com.netflix.spinnaker.cats.agent.ExecutionInstrumentation;
import com.netflix.spinnaker.cats.cluster.AgentIntervalProvider;
import com.netflix.spinnaker.cats.cluster.NodeStatusProvider;
import com.netflix.spinnaker.cats.module.CatsModuleAware;
import com.netflix.spinnaker.cats.provider.ProviderRegistry;
import com.netflix.spinnaker.clouddriver.config.PubSubSchedulerProperties;
import com.netflix.spinnaker.kork.annotations.Alpha;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.connection.stream.StreamInfo;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

/**
 * This is an implementation of a scheduler system for spinnaker that operates with a slightly
 * DIFFERENT approach.
 *
 * <p>There are three key components to this scheduler: 1) A state management machine that tracks
 * known agents and their current processing state. This knows ALL known agents and their state. It
 * also tracks the last time an agent was run, and the last time it was scheduled to run. 2) A
 * poller that looks up the state and queues agents to be run based upon state/status 3) A message
 * handler that given an agent identified by "agentType", will actually do the agent execution. This
 * will ALSO update the state.
 *
 * <p>This implementation publishes execution requests to a redis STREAM consumed through a consumer
 * group ({@link PubSubAgentRunner#CONSUMER_GROUP}): each record is delivered to exactly one
 * consumer, records are durable across replica restarts, and runners pull work only when they have
 * free capacity.
 *
 * <p>Coordination notes: EVERY replica runs this poller (there is no leader election). Correctness
 * relies on the conditional state transitions in {@link StateMachine#tryTransition}: a record is
 * only published by the scheduler instance that WINS the transition to PENDING, and only the runner
 * instance that wins the transition to RUNNING will execute the agent. The stream is purely the
 * dispatch channel - all retry/recovery decisions live in the SQL state machine sweeps.
 */
@Component
@ConditionalOnProperty("cats.pubsub.enabled")
@Log4j2
@Alpha
public class PubSubAgentScheduler extends CatsModuleAware
    implements Runnable, AgentScheduler<AgentLock> {

  public static final Set<StateMachine.State> QUEUEABLE_STATES =
      Set.of(
          StateMachine.State.FINISHED, StateMachine.State.NOT_STARTED, StateMachine.State.FAILED);
  public static final String STREAM_KEY = "pubsub_agent_stream";
  public static final String AGENT_TYPE_FIELD = "agentType";
  @Autowired private StateMachine stateMachine;
  @Autowired private AgentIntervalProvider intervalProvider;
  @Autowired @Lazy public ProviderRegistry providerRegistry;
  @Autowired RedisTemplate<String, String> redisTemplate;
  @Autowired PubSubSchedulerProperties properties;
  @Autowired MeterRegistry meterRegistry;

  // Optional: only present when a discovery/health integration is configured.  Used to stop
  // scheduling while this replica is disabled (e.g. draining for shutdown).
  @Autowired(required = false)
  NodeStatusProvider nodeStatusProvider;

  // Backing values for the per-state gauges.  Micrometer gauges hold weak references, so the map
  // (owned by this bean) keeps them alive; the AtomicLongs are updated in place each cycle.
  private final Map<String, AtomicLong> agentStateGaugeValues = new ConcurrentHashMap<>();

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
      stateMachine.createOrUpdateAgent(agent.getAgentType(), StateMachine.State.NOT_STARTED);
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

  @Override
  @Scheduled(fixedDelayString = "${cats.pubsub.delay-between-scheduler-runs-ms:15000}")
  public void run() {
    if (nodeStatusProvider != null && !nodeStatusProvider.isNodeEnabled()) {
      log.debug("Node is not enabled (draining or out of discovery); skipping scheduler cycle");
      return;
    }
    log.debug("Running agent scheduler to look for new agents to enqueue");
    StopWatch cycleTimer = new StopWatch();
    cycleTimer.start();
    // One pass over the registry per cycle instead of a full registry scan per candidate agent -
    // with thousands of agents the per-candidate lookup is quadratic and dominates the cycle.
    Map<String, Agent> agentsByType = agentsByType();
    requeueStuckRunningAgents(agentsByType);
    queueEligibleAgents(agentsByType);
    requeueStalePendingAgents();
    purgeAgentsMarkedForDeletion();
    trimStream();
    publishStateGauges();
    cycleTimer.stop();
    meterRegistry
        .timer("cats.pubsub.scheduler.cycle.duration")
        .record(cycleTimer.getTotalTimeMillis(), TimeUnit.MILLISECONDS);
    log.debug("Done running the agent scheduler in {} ms", cycleTimer.getTotalTimeMillis());
  }

  private Map<String, Agent> agentsByType() {
    Map<String, Agent> agents = new HashMap<>();
    providerRegistry
        .getProviders()
        .forEach(
            provider ->
                provider
                    .getAgents()
                    .forEach(agent -> agents.putIfAbsent(agent.getAgentType(), agent)));
    return agents;
  }

  /**
   * Find any agents still running after their max timeout and reschedule them. They likely failed
   * or were hung. UNFORTUNATELY there's no "interrupt" option on Agents in spinnaker to stop the
   * original execution :( NOT Yet :( SO we just reschedule the execution instead. The original
   * execution (if actually still alive) will continue to hold the redis lock, so the requeued
   * message is dropped by runners until the lock is released.
   */
  private void requeueStuckRunningAgents(Map<String, Agent> agentsByType) {
    stateMachine
        .listAgentsFilteredWhereIn(Set.of(StateMachine.State.RUNNING))
        .forEach(
            runningAgent -> {
              Agent agent = agentsByType.get(runningAgent.getAgentType());
              if (agent == null) {
                log.warn(
                    "Agent {} is marked RUNNING but is no longer in the provider registry.  Skipping timeout checks on it.",
                    runningAgent.getAgentType());
                return;
              }
              AgentIntervalProvider.Interval interval = intervalProvider.getInterval(agent);
              if (System.currentTimeMillis() - runningAgent.getLastTransitionTime()
                  < interval.getTimeout()) {
                return;
              }
              try {
                log.warn(
                    "Found agent {} that has been in a running state longer than its timeout (last transition {}).  Rescheduling.  WARNING:  The original execution MAY still be running... check agent logs and duration data!",
                    runningAgent.getAgentType(),
                    runningAgent.getLastTransitionTime());
                if (stateMachine.tryTransition(
                        runningAgent.getAgentType(),
                        Set.of(StateMachine.State.RUNNING),
                        StateMachine.State.PENDING)
                    > 0) {
                  enqueue(runningAgent.getAgentType());
                  countQueued("stuck-running");
                }
              } catch (Exception e) {
                log.error(
                    "Failed to send agent {} for processing OR update state!  ",
                    runningAgent.getAgentType(),
                    e);
              }
            });
  }

  /**
   * Queue any agents whose interval has elapsed. The transition to PENDING is a conditional update
   * - whichever scheduler replica wins the transition publishes the message; everyone else skips.
   */
  private void queueEligibleAgents(Map<String, Agent> agentsByType) {
    stateMachine
        .listAgentsFilteredWhereIn(QUEUEABLE_STATES)
        .forEach(
            candidate -> {
              Agent agent = agentsByType.get(candidate.getAgentType());
              if (agent == null) {
                log.warn(
                    "Can't find agent "
                        + candidate.getAgentType()
                        + " in the list of available provider agents!  Will skip scheduling and try on next run.  IF you see this a lot theres probably a bug someplace on agent lists/registry behavior...");
                return;
              }
              AgentIntervalProvider.Interval interval = intervalProvider.getInterval(agent);
              if (!isDueToRun(candidate, interval)) {
                return;
              }
              try {
                if (stateMachine.tryTransition(
                        candidate.getAgentType(), QUEUEABLE_STATES, StateMachine.State.PENDING)
                    > 0) {
                  log.debug(
                      "Queueing agent {} and moving to PENDING status", candidate.getAgentType());
                  enqueue(candidate.getAgentType());
                  countQueued("interval");
                  recordScheduleLateness(candidate, interval);
                }
              } catch (Exception e) {
                log.error(
                    "Failed to queue agent {}.  It's LIKELY this agent was not processed at all and will be retried on the next scheduler cycle.",
                    candidate.getAgentType(),
                    e);
              }
            });
  }

  private boolean isDueToRun(
      StateMachine.AgentState candidate, AgentIntervalProvider.Interval interval) {
    long now = System.currentTimeMillis();
    if (StateMachine.State.FAILED.name().equals(candidate.getCurrentState())) {
      // Back off failed agents based on WHEN they failed - lastExecutionTime is only written on
      // successful completion (and is 0 for agents that have never completed).
      return now - candidate.getLastTransitionTime() >= interval.getErrorInterval();
    }
    return now - candidate.getLastExecutionTime() >= interval.getInterval();
  }

  /**
   * How far past its due time (last execution + interval) an agent is when we queue it. A healthy
   * system stays under one scheduler cycle; growth here means the runners can't keep up with the
   * number of agents due.
   */
  private void recordScheduleLateness(
      StateMachine.AgentState candidate, AgentIntervalProvider.Interval interval) {
    // Agents that have never completed (lastExecutionTime == 0) would report nonsense lateness.
    if (candidate.getLastExecutionTime() <= 0) {
      return;
    }
    long dueAt = candidate.getLastExecutionTime() + interval.getInterval();
    meterRegistry
        .timer("cats.pubsub.schedule.lateness")
        .record(Math.max(0, System.currentTimeMillis() - dueAt), TimeUnit.MILLISECONDS);
  }

  private void countQueued(String reason) {
    meterRegistry
        .counter("cats.pubsub.scheduler.queued", List.of(Tag.of("reason", reason)))
        .increment();
  }

  /**
   * If an agent has been PENDING for longer than configured, its stream record was lost (e.g.
   * trimmed away by an extreme backlog, or the XADD failed after the state transition). Re-enqueue
   * it. The PENDING->PENDING transition bumps last_transition_time so this fires once per
   * configured window rather than on every scheduler cycle.
   */
  private void requeueStalePendingAgents() {
    long maxPendingMillis =
        Duration.ofMinutes(properties.getMinutesBeforeReQueueOfAgents()).toMillis();
    stateMachine
        .listAgentsFilteredWhereIn(Set.of(StateMachine.State.PENDING))
        .forEach(
            pendingAgent -> {
              if (System.currentTimeMillis() - pendingAgent.getLastTransitionTime()
                  < maxPendingMillis) {
                return;
              }
              try {
                if (stateMachine.tryTransition(
                        pendingAgent.getAgentType(),
                        Set.of(StateMachine.State.PENDING),
                        StateMachine.State.PENDING)
                    > 0) {
                  log.warn(
                      "Agent {} has been PENDING for over {} minutes - the stream record was likely lost or trimmed.  Re-enqueueing it.",
                      pendingAgent.getAgentType(),
                      properties.getMinutesBeforeReQueueOfAgents());
                  enqueue(pendingAgent.getAgentType());
                  countQueued("stale-pending");
                }
              } catch (Exception e) {
                log.error(
                    "Failed to re-queue stale pending agent {}", pendingAgent.getAgentType(), e);
              }
            });
  }

  /**
   * Remove any agent marked as DELETED which hasn't changed in the configured window. The delay
   * exists because some providers "reschedule" agents via an unschedule/reschedule pair, and we
   * want an in-flight execution to still see the DELETED marker when it completes.
   */
  private void purgeAgentsMarkedForDeletion() {
    long purgeAfterMillis =
        Duration.ofMinutes(properties.getMinutesBeforeDeletingMarkedForDeletion()).toMillis();
    stateMachine
        .listAgentsFilteredWhereIn(Set.of(StateMachine.State.DELETED))
        .forEach(
            deletedAgent -> {
              if (System.currentTimeMillis() - deletedAgent.getLastTransitionTime()
                  >= purgeAfterMillis) {
                stateMachine.delete(deletedAgent.getAgentType());
                meterRegistry.counter("cats.pubsub.scheduler.purged").increment();
              }
            });
  }

  /** Publish an execution request for an agent onto the stream. */
  private void enqueue(String agentType) {
    redisTemplate.opsForStream().add(STREAM_KEY, Map.of(AGENT_TYPE_FIELD, agentType));
  }

  /**
   * Cap the stream length. Acknowledged records are not removed by redis automatically, so without
   * trimming the stream grows forever. The cap is generous: if a backlog ever exceeds it, the
   * oldest (almost certainly already-consumed) records are dropped, and the stale-PENDING sweep
   * re-publishes anything that was still live.
   */
  private void trimStream() {
    try {
      redisTemplate.opsForStream().trim(STREAM_KEY, properties.getStreamMaxLength(), true);
    } catch (Exception e) {
      log.warn("Unable to trim the agent stream", e);
    }
  }

  /** Gauge of how many agents sit in each state - the primary "is the system healthy" signal. */
  private void publishStateGauges() {
    try {
      Map<String, Long> counts = stateMachine.countAgentsByState();
      for (StateMachine.State state : StateMachine.State.values()) {
        agentStateGaugeValues
            .computeIfAbsent(
                state.name(),
                name ->
                    meterRegistry.gauge(
                        "cats.pubsub.agents.state", Tags.of("state", name), new AtomicLong()))
            .set(counts.getOrDefault(state.name(), 0L));
      }
      // The LIVE queue depth is the number of PENDING agents (published but not yet claimed).
      // Stream length (XLEN) is NOT an overload signal: acknowledged records accumulate until the
      // trim, so depth sits at streamMaxLength in steady state by design.  What overload actually
      // breaks is the trim eating records that are still live - warn well before that can happen.
      long liveBacklog = counts.getOrDefault(StateMachine.State.PENDING.name(), 0L);
      if (liveBacklog > properties.getStreamMaxLength() / 2) {
        log.warn(
            "There are {} agents PENDING against a stream capacity of {}.  If the backlog reaches the cap, the per-cycle trim will start discarding live records (the stale-PENDING sweep will recover them, slowly).  Add runner capacity or raise cats.pubsub.streamMaxLength.",
            liveBacklog,
            properties.getStreamMaxLength());
      }
    } catch (Exception e) {
      log.warn("Unable to publish agent state gauges", e);
    }
    publishStreamGauges();
  }

  /**
   * Stream-side observability: depth (XLEN - total records incl. consumed-until-trimmed), in-flight
   * (delivered to a runner, not yet acknowledged) and the number of live consumers in the group (~=
   * replicas currently pulling work).
   */
  private void publishStreamGauges() {
    try {
      Long depth = redisTemplate.opsForStream().size(STREAM_KEY);
      streamGauge("stream-depth", "cats.pubsub.stream.depth").set(depth == null ? 0 : depth);
      StreamInfo.XInfoGroups groups = redisTemplate.opsForStream().groups(STREAM_KEY);
      groups.stream()
          .filter(group -> PubSubAgentRunner.CONSUMER_GROUP.equals(group.groupName()))
          .findFirst()
          .ifPresent(
              group -> {
                streamGauge("stream-pending", "cats.pubsub.stream.pending")
                    .set(group.pendingCount());
                streamGauge("stream-consumers", "cats.pubsub.stream.consumers")
                    .set(group.consumerCount());
              });
    } catch (Exception e) {
      // Expected until the stream and consumer group exist (first startup).
      log.debug("Unable to publish stream gauges: {}", e.getMessage());
    }
  }

  private AtomicLong streamGauge(String key, String metricName) {
    return agentStateGaugeValues.computeIfAbsent(
        key, k -> meterRegistry.gauge(metricName, Tags.empty(), new AtomicLong()));
  }
}
