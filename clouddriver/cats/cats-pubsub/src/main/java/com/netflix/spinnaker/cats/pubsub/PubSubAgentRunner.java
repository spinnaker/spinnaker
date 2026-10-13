package com.netflix.spinnaker.cats.pubsub;

import com.netflix.spinnaker.cats.agent.Agent;
import com.netflix.spinnaker.cats.agent.CacheResult;
import com.netflix.spinnaker.cats.agent.CachingAgent;
import com.netflix.spinnaker.cats.agent.ExecutionInstrumentation;
import com.netflix.spinnaker.cats.cluster.NodeStatusProvider;
import com.netflix.spinnaker.cats.provider.ProviderRegistry;
import com.netflix.spinnaker.clouddriver.config.PubSubSchedulerProperties;
import com.netflix.spinnaker.kork.annotations.Alpha;
import com.netflix.spinnaker.kork.pubsub.model.MessageChannel;
import com.netflix.spinnaker.kork.pubsub.model.MessageSubscription;
import com.netflix.spinnaker.kork.pubsub.model.PubsubMessage;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

/**
 * Pulls agent execution requests from a redis stream consumer group and runs them.
 *
 * <p>Record delivery is handled by Kork's Redis Streams subscription, which owns the poll loop,
 * connection management, and subscription lifecycle. This class supplies the pieces the container
 * doesn't: concurrency limiting (a semaphore-gated worker pool - the container invokes the listener
 * on its single poll thread, so blocking that thread until a worker is free is exactly the
 * backpressure we want), reclaim of records abandoned by dead consumers, and pausing consumption
 * while the node is disabled in discovery.
 *
 * <p>Concurrency is still ultimately governed by the SQL state machine: the PENDING->RUNNING
 * conditional transition is the mutex that guarantees single execution, and its sweeps
 * (stuck-RUNNING / stale-PENDING) are the retry authority. The stream is "just" the durable
 * dispatch channel, which is why records are always acknowledged after a claim decision -
 * redelivering a record could never execute anyway, because the state machine gates it.
 */
@Log4j2
@Component
@ConditionalOnProperty("cats.pubsub.enabled")
@Alpha
public class PubSubAgentRunner
    implements ApplicationListener<ApplicationReadyEvent>, DisposableBean {
  public static final String CONSUMER_GROUP = "agent-runners";

  private static final int RECLAIM_SCAN_LIMIT = 100;

  // States a runner is allowed to claim work from.  The scheduler always transitions an agent to
  // PENDING before publishing, so anything else means another runner already claimed it (or it was
  // deleted).
  private static final Set<StateMachine.State> CLAIMABLE_STATES =
      Set.of(StateMachine.State.PENDING);

  @Autowired private ProviderRegistry providerRegistry;
  @Autowired StateMachine stateMachine;
  @Autowired MeterRegistry meterRegistry;
  @Autowired PubSubSchedulerProperties properties;

  @Autowired
  @Qualifier("agentExecutionChannel")
  MessageChannel messageChannel;

  @Autowired(required = false)
  Collection<ExecutionInstrumentation> executionInstrumentations = List.of();

  // Optional: only present when a discovery/health integration is configured.  Used to stop
  // claiming new work while this replica is disabled (e.g. draining for shutdown).
  @Autowired(required = false)
  NodeStatusProvider nodeStatusProvider;

  private MessageSubscription subscription;
  private ExecutorService workers;
  private Semaphore inFlight;

  /**
   * Start on ApplicationReadyEvent rather than bean initialization: providers register their agents
   * during context startup, and consuming before that would drop every record as "unknown agent".
   * (This is also why the container is created here instead of as a SmartLifecycle bean - lifecycle
   * beans start before the ready event fires.)
   */
  @Override
  public synchronized void onApplicationEvent(ApplicationReadyEvent event) {
    if (subscription != null) {
      return;
    }
    int concurrency = properties.getMaxConcurrentAgents();
    inFlight = new Semaphore(concurrency);
    AtomicInteger threadNumber = new AtomicInteger();
    workers =
        Executors.newFixedThreadPool(
            concurrency,
            runnable -> {
              Thread thread =
                  new Thread(runnable, "pubsub-agent-runner-" + threadNumber.incrementAndGet());
              thread.setDaemon(true);
              return thread;
            });
    subscription =
        messageChannel.subscribe(
            "agent-runner",
            concurrency,
            this::dispatch,
            error -> {
              meterRegistry.counter("cats.pubsub.stream.poll.errors").increment();
              log.error("Error polling the agent stream; the subscription will retry", error);
            });
    if (nodeStatusProvider == null || nodeStatusProvider.isNodeEnabled()) {
      subscription.start();
    }
    log.info(
        "Started agent subscription in group {} with {} worker threads",
        CONSUMER_GROUP,
        concurrency);
  }

  @Override
  public synchronized void destroy() {
    if (subscription != null) {
      subscription.close();
    }
    if (workers != null) {
      workers.shutdown();
    }
  }

  /**
   * Periodic maintenance: pause/resume consumption based on node discovery status, and rescue
   * records that were delivered to a consumer (typically a replica that died or hung) but never
   * acknowledged. The state machine still gates execution, so reclaiming a record whose agent is
   * genuinely running elsewhere just results in an acknowledge-and-drop.
   */
  @Scheduled(fixedDelay = 60_000)
  synchronized void maintain() {
    if (subscription == null) {
      return; // application not fully started yet
    }
    boolean nodeEnabled = nodeStatusProvider == null || nodeStatusProvider.isNodeEnabled();
    if (!nodeEnabled) {
      if (subscription.isRunning()) {
        log.info("Node is disabled (draining or out of discovery); pausing stream consumption");
        // The shared subscription re-registers its poll loop on resume.
        subscription.stop();
      }
      return;
    }
    if (!subscription.isRunning()) {
      log.info("Node is enabled again; resuming stream consumption");
      subscription.start();
    }
    reclaimAbandonedRecords();
  }

  /**
   * Hand a record to the worker pool. Called on the container's poll thread: when every worker is
   * busy, the acquire blocks that thread, which stops the container from reading more records -
   * that IS the backpressure, so work queues durably in redis instead of in process memory.
   */
  private void dispatch(PubsubMessage record) {
    try {
      inFlight.acquire();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return;
    }
    try {
      workers.execute(
          () -> {
            try {
              processRecord(record);
            } finally {
              inFlight.release();
            }
          });
    } catch (RejectedExecutionException e) {
      inFlight.release();
      // Leave pending for another replica if shutdown raced with dispatch.
      log.debug("Worker pool stopped before dispatch", e);
    }
  }

  private void reclaimAbandonedRecords() {
    try {
      Duration minIdle = Duration.ofMinutes(properties.getMinutesBeforeReQueueOfAgents());
      long reclaimed = subscription.reclaim(minIdle, RECLAIM_SCAN_LIMIT);
      meterRegistry.counter("cats.pubsub.stream.reclaimed").increment(reclaimed);
    } catch (Exception e) {
      log.warn("Unable to scan for abandoned stream records", e);
    }
  }

  private void processRecord(PubsubMessage record) {
    Object agentType = record.attributes().get(PubSubAgentScheduler.AGENT_TYPE_FIELD);
    try {
      if (agentType == null) {
        log.warn("Dropping malformed stream record {} with no agentType field", record.id());
        return;
      }
      runAgent(agentType.toString());
    } catch (Exception e) {
      // runAgent handles its own failures; this only catches bugs in the claim plumbing itself.
      log.error("Unexpected error processing stream record for agent {}", agentType, e);
    } finally {
      // Always acknowledge: retries are owned by the SQL state machine sweeps, which publish NEW
      // records.  Redelivering this record could never execute anyway - the agent's state row is
      // no longer PENDING.
      try {
        subscription.ack(record);
      } catch (Exception e) {
        log.warn("Failed to acknowledge stream record {}", record.id(), e);
      }
    }
  }

  private void runAgent(String agentType) {
    log.debug("Starting execution request for agent {}", agentType);
    Agent agent = providerRegistry.getAgentForProviderName(agentType);
    if (agent == null) {
      log.warn(
          "Received an execution request for agent {} but it is not in the provider registry.  Dropping - it will be retried by the scheduler if the agent reappears.",
          agentType);
      return;
    }
    StateMachine.AgentState agentState = stateMachine.getAgent(agentType);
    if (agentState == null) {
      log.warn(
          "Received an execution request for agent {} but there is no state row for it (was it deleted?).  Dropping.",
          agentType);
      return;
    }

    // The conditional PENDING->RUNNING transition is the execution mutex: a consumer group already
    // delivers each record to only one consumer, but this also protects against the sweeps
    // publishing a duplicate record for an agent that is already being handled.
    if (stateMachine.tryTransition(agentType, CLAIMABLE_STATES, StateMachine.State.RUNNING) == 0) {
      log.debug(
          "Agent {} was already claimed by another runner (or deleted).  Skipping.", agentType);
      return;
    }
    // The PENDING transition timestamp is when the scheduler published the record, so this is the
    // time the agent spent waiting in the stream before a runner picked it up.
    meterRegistry
        .timer("cats.pubsub.queue.latency", List.of(Tag.of("agentType", agentType)))
        .record(
            Math.max(0, System.currentTimeMillis() - agentState.getLastTransitionTime()),
            TimeUnit.MILLISECONDS);

    StopWatch totalDuration = new StopWatch();
    AtomicInteger totalDataCached = new AtomicInteger(-1);
    long startTimeMs = System.currentTimeMillis();
    try {
      totalDuration.start();
      executionInstrumentations.forEach(i -> i.executionStarted(agent));
      executeAgent(agent, agentType, totalDataCached);
      totalDuration.stop();
      stateMachine.markAgentCompleted(
          agent.getAgentType(), totalDuration.getTotalTimeMillis(), totalDataCached.get());
      executionInstrumentations.forEach(
          i -> i.executionCompleted(agent, ExecutionInstrumentation.elapsedTimeMs(startTimeMs)));
      meterRegistry
          .timer(
              "cats.pubsub.execution.totaltime",
              List.of(
                  Tag.of("agentType", agentType),
                  Tag.of("state", StateMachine.State.FINISHED.name())))
          .record(totalDuration.getTotalTimeMillis(), TimeUnit.MILLISECONDS);
    } catch (Exception e) {
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
}
