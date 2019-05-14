/*
 * Copyright 2017 Netflix, Inc.
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
package com.netflix.spinnaker.cats.dynomite.cluster;

import static java.lang.String.format;

import com.netflix.spinnaker.cats.agent.*;
import com.netflix.spinnaker.cats.cluster.AgentIntervalProvider;
import com.netflix.spinnaker.cats.cluster.NodeStatusProvider;
import com.netflix.spinnaker.cats.dynomite.DynomiteUtils;
import com.netflix.spinnaker.cats.dynomite.ExcessiveDynoFailureRetries;
import com.netflix.spinnaker.cats.module.CatsModuleAware;
import com.netflix.spinnaker.cats.redis.cluster.ClusteredSortAgentLock;
import com.netflix.spinnaker.cats.thread.NamedThreadFactory;
import com.netflix.spinnaker.kork.jedis.RedisClientDelegate;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * rz NOTE: This is functionally the same (changes listed below) as ClusteredSortAgentScheduler, but
 * with early support for Dynomite. Has been tested more, it'll be rolled up into the original
 * class.
 *
 * <p>1. Dynomite does not yet support scriptLoad/evalsha. This class just uses eval. 1. We need to
 * ensure the WAITING and WORKING sets are on the same shard, so all keys use hashtags. 1.
 * RedisClientDelgate over JedisPool. 1. Instead of retrieving the time from Redis, clouddriver is
 * responsible for generating scores from Clock. Prefer how the Jedis-based class does it, but time
 * is not supported in Dynomite. 1. Supports scheduling non-caching agent executions. Mandatory for
 * AWS provider. 1. Retries on connection errors.
 */
public class DynoClusteredSortAgentScheduler extends CatsModuleAware
    implements AgentScheduler<ClusteredSortAgentLock>, Runnable {
  private enum Status {
    SUCCESS,
    FAILURE
  }

  private final Clock clock;
  private final RedisClientDelegate redisClientDelegate;
  private final NodeStatusProvider nodeStatusProvider;
  private final AgentIntervalProvider intervalProvider;
  private final ExecutorService agentWorkPool;

  private static final int NOW = 0;
  private static final int REDIS_REFRESH_PERIOD = 30;
  private int runCount = 0;

  private final Logger log;

  private Map<String, AgentWorker> agents;
  private Optional<Semaphore> runningAgents;

  // This code assumes that every agent being run is in exactly either the WAITING or WORKING set.
  private static final String WAITING_SET = "{scheduler}:WAITZ";
  private static final String WORKING_SET = "{scheduler}:WORKZ";
  private static final String ADD_AGENT_SCRIPT = "addAgentScript";
  private static final String VALID_SCORE_SCRIPT = "validScoreScript";
  private static final String SWAP_SET_SCRIPT = "swapSetScript";
  private static final String REMOVE_AGENT_SCRIPT = "removeAgentScript";
  private static final String CONDITIONAL_SWAP_SET_SCRIPT = "conditionalSwapSetScript";

  private static final RetryPolicy RETRY_POLICY = DynomiteUtils.greedyRetryPolicy(3000);

  private ConcurrentHashMap<String, String> scripts;

  public DynoClusteredSortAgentScheduler(
      Clock clock,
      RedisClientDelegate redisClientDelegate,
      NodeStatusProvider nodeStatusProvider,
      AgentIntervalProvider intervalProvider,
      Integer parallelism) {
    this.clock = clock;
    this.redisClientDelegate = redisClientDelegate;
    this.nodeStatusProvider = nodeStatusProvider;
    this.agents = new ConcurrentHashMap<>();
    this.intervalProvider = intervalProvider;
    this.log = LoggerFactory.getLogger(getClass());

    if (parallelism == 0 || parallelism < -1) {
      throw new IllegalArgumentException(
          "Argument 'parallelism' must be positive, or -1 (for unlimited parallelism).");
    } else if (parallelism > 0) {
      this.runningAgents = Optional.of(new Semaphore(parallelism));
    } else {
      this.runningAgents = Optional.empty();
    }

    scripts = new ConcurrentHashMap<>();
    storeScripts();

    this.agentWorkPool =
        Executors.newCachedThreadPool(new NamedThreadFactory(AgentWorker.class.getSimpleName()));
    Executors.newSingleThreadScheduledExecutor(
            new NamedThreadFactory(DynoClusteredSortAgentScheduler.class.getSimpleName()))
        .scheduleAtFixedRate(this, 0, 1, TimeUnit.SECONDS);
  }

  private void storeScripts() {
    // When we switch an agent from one set to another, we first make sure it exists in the set we
    // are removing it
    // from, and then we perform the swap. If this check fails, the thread performing the swap does
    // not get ownership
    // of the agent.
    // Swap happens from KEYS[1] -> KEYS[2] with the agent type being ARGV[1], and the score being
    // ARGV[2].
    scripts.put(
        SWAP_SET_SCRIPT,
        "local score = redis.call('zscore', KEYS[1], ARGV[1])\n"
            + "if score ~= nil then\n"
            + "  redis.call('zrem', KEYS[1], ARGV[1])\n"
            + "  redis.call('zadd', KEYS[2], ARGV[2], ARGV[1])\n"
            + "  return score\n"
            + "else return nil end\n");

    scripts.put(
        CONDITIONAL_SWAP_SET_SCRIPT,
        "local score = redis.call('zscore', KEYS[1], ARGV[1])\n"
            + "if score == ARGV[3] then\n"
            + "  redis.call('zrem', KEYS[1], ARGV[1])\n"
            + "  redis.call('zadd', KEYS[2], ARGV[2], ARGV[1])\n"
            + "  return score\n"
            + "else return nil end\n");

    scripts.put(
        VALID_SCORE_SCRIPT,
        "local score = redis.call('zscore', KEYS[1], ARGV[1])\n"
            + "if score == ARGV[2] then\n"
            + "  return score\n"
            + "else return nil end\n");

    // If the agent isn't present in either the WAITING or WORKING sets, it's safe to add. If it's
    // present in either,
    // it's being worked on or was recently run, so leave it be.
    // KEYS[1] and KEYS[2] are checked for inclusion. If the agent is in neither ARGV[1] is added to
    // KEYS[1] with score
    // ARGV[2].
    scripts.put(
        ADD_AGENT_SCRIPT,
        "if redis.call('zrank', KEYS[1], ARGV[1]) ~= nil then\n"
            + "  if redis.call('zrank', KEYS[2], ARGV[1]) ~= nil then\n"
            + "    return redis.call('zadd', KEYS[1], ARGV[2], ARGV[1])\n"
            + "  else return nil end\n"
            + "else return nil end\n");

    scripts.put(
        REMOVE_AGENT_SCRIPT,
        "redis.call('zrem', KEYS[1], ARGV[1])\n" + "redis.call('zrem', KEYS[2], ARGV[1])\n");
  }

  private String getScript(String scriptName) {
    String scriptSha = scripts.get(scriptName);
    if (scriptSha == null) {
      storeScripts();
      scriptSha = scripts.get(scriptName);
      if (scriptSha == null) {
        throw new RuntimeException("Failed to load caching scripts.");
      }
    }
    return scripts.get(scriptName);
  }

  @Override
  public void schedule(
      Agent agent,
      AgentExecution agentExecution,
      ExecutionInstrumentation executionInstrumentation) {
    if (agent instanceof AgentSchedulerAware) {
      ((AgentSchedulerAware) agent).setAgentScheduler(this);
    }

    withRetry(
        format("Scheduling %s", agent.getAgentType()),
        () ->
            redisClientDelegate.withScriptingClient(
                client -> {
                  client.eval(
                      getScript(ADD_AGENT_SCRIPT),
                      2,
                      WAITING_SET,
                      WORKING_SET,
                      agent.getAgentType(),
                      score(NOW));
                }));
    agents.put(
        agent.getAgentType(),
        new AgentWorker(agent, agentExecution, executionInstrumentation, this));
  }

  @Override
  public ClusteredSortAgentLock tryLock(Agent agent) {
    ScoreTuple scores = acquireAgent(agent);
    if (scores != null) {
      return new ClusteredSortAgentLock(agent, scores.acquireScore, scores.releaseScore);
    } else {
      return null;
    }
  }

  @Override
  public boolean tryRelease(ClusteredSortAgentLock lock) {
    return conditionalReleaseAgent(lock.getAgent(), lock.getAcquireScore(), lock.getReleaseScore())
        != null;
  }

  @Override
  public boolean lockValid(ClusteredSortAgentLock lock) {
    return withRetry(
        format("Checking if lock is valid for %s", lock.getAgent().getAgentType()),
        () ->
            redisClientDelegate.withScriptingClient(
                client ->
                    client.eval(
                            getScript(VALID_SCORE_SCRIPT),
                            1,
                            WORKING_SET,
                            lock.getAgent().getAgentType(),
                            lock.getAcquireScore())
                        != null));
  }

  public void unschedule(Agent agent) {
    agents.remove(agent.getAgentType());
    withRetry(
        format("Unscheduling %s", agent.getAgentType()),
        () ->
            redisClientDelegate.withScriptingClient(
                client -> {
                  client.eval(
                      getScript(REMOVE_AGENT_SCRIPT),
                      2,
                      WAITING_SET,
                      WORKING_SET,
                      agent.getAgentType());
                }));
  }

  @Override
  public boolean isAtomic() {
    return true;
  }

  @Override
  public void run() {
    if (!nodeStatusProvider.isNodeEnabled()) {
      return;
    }
    try {
      saturatePool();
    } catch (Throwable t) {
      log.error("Failed to run caching agents", t);
    } finally {
      runCount++;
    }
  }

  private String score(long offsetSeconds) {
    return format("%d", clock.instant().plus(offsetSeconds, ChronoUnit.SECONDS).getEpochSecond());
  }

  private ScoreTuple acquireAgent(Agent agent) {
    String acquireScore = score(intervalProvider.getInterval(agent).getTimeout());
    Object releaseScore =
        withRetry(
            format("Acquiring lock on %s", agent.getAgentType()),
            () ->
                redisClientDelegate.withScriptingClient(
                    client -> {
                      return client.eval(
                          getScript(SWAP_SET_SCRIPT),
                          Arrays.asList(WAITING_SET, WORKING_SET),
                          Arrays.asList(agent.getAgentType(), acquireScore));
                    }));
    return releaseScore != null ? new ScoreTuple(acquireScore, releaseScore.toString()) : null;
  }

  private ScoreTuple conditionalReleaseAgent(Agent agent, String acquireScore, Status status) {
    long newInterval =
        status == Status.SUCCESS
            ? intervalProvider.getInterval(agent).getInterval()
            : intervalProvider.getInterval(agent).getErrorInterval();
    String newAcquireScore = score(newInterval);

    Object releaseScore =
        withRetry(
            format("Conditionally releasing %s", agent.getAgentType()),
            () ->
                redisClientDelegate.withScriptingClient(
                    client -> {
                      return client.eval(
                          getScript(CONDITIONAL_SWAP_SET_SCRIPT),
                          Arrays.asList(WORKING_SET, WAITING_SET),
                          Arrays.asList(agent.getAgentType(), newAcquireScore, acquireScore));
                    }));

    return releaseScore != null ? new ScoreTuple(newAcquireScore, releaseScore.toString()) : null;
  }

  private ScoreTuple conditionalReleaseAgent(
      Agent agent, String acquireScore, String newAcquireScore) {
    Object releaseScore =
        withRetry(
            format("Conditionally releasing %s", agent.getAgentType()),
            () ->
                redisClientDelegate.withScriptingClient(
                    client -> {
                      return client
                          .eval(
                              getScript(CONDITIONAL_SWAP_SET_SCRIPT),
                              Arrays.asList(WORKING_SET, WAITING_SET),
                              Arrays.asList(agent.getAgentType(), newAcquireScore, acquireScore))
                          .toString();
                    }));
    return releaseScore != null ? new ScoreTuple(newAcquireScore, releaseScore.toString()) : null;
  }

  private ScoreTuple releaseAgent(Agent agent) {
    String acquireScore = score(intervalProvider.getInterval(agent).getInterval());
    Object releaseScore =
        withRetry(
            format("Releasing %s", agent.getAgentType()),
            () ->
                redisClientDelegate.withScriptingClient(
                    client -> {
                      return client
                          .eval(
                              getScript(SWAP_SET_SCRIPT),
                              Arrays.asList(WORKING_SET, WAITING_SET),
                              Arrays.asList(agent.getAgentType(), acquireScore))
                          .toString();
                    }));
    return releaseScore != null ? new ScoreTuple(acquireScore, releaseScore.toString()) : null;
  }

  private void saturatePool() {
    withRetry(
        "Repopulating agents into waiting set",
        () ->
            redisClientDelegate.withScriptingClient(
                client -> {
                  // Occasionally repopulate the agents in case redis went down. If they already
                  // exist, this is a NOOP
                  if (runCount % REDIS_REFRESH_PERIOD == 0) {
                    for (String agent : agents.keySet()) {
                      client.eval(
                          getScript(ADD_AGENT_SCRIPT),
                          2,
                          WAITING_SET,
                          WORKING_SET,
                          agent,
                          score(NOW));
                    }
                  }
                }));

    List<String> keys =
        withRetry(
            "Getting available agents",
            () ->
                redisClientDelegate.withCommandsClient(
                    client -> {
                      // First cull threads in the WORKING set that have been there too long
                      // (TIMEOUT time).
                      Set<String> oldKeys = client.zrangeByScore(WORKING_SET, "-inf", score(NOW));
                      for (String key : oldKeys) {
                        // Ignore result, since if this agent was released between now and the above
                        // jedis call, our work was done
                        // for us.
                        AgentWorker worker = agents.get(key);
                        if (worker != null) {
                          releaseAgent(worker.agent);
                        }
                      }

                      // Now look for agents that have been in the queue for at least INTERVAL time.
                      return new ArrayList<>(client.zrangeByScore(WAITING_SET, "-inf", score(NOW)));
                    }));

    Set<AgentWorker> workers = new HashSet<>();

    // Loop until we either run out of threads to use, or agents (which are keys) to run.
    while (!keys.isEmpty() && runningAgents.map(Semaphore::tryAcquire).orElse(true)) {
      String agent = keys.remove(0);

      AgentWorker worker = agents.get(agent);
      ScoreTuple score;
      if (worker != null && (score = acquireAgent(worker.agent)) != null) {
        // This score is used to determine if the worker thread running the agent is allowed to
        // store its results.
        // If on release of this agent, the scores don't match, this agent was rescheduled by a
        // separate thread.
        worker.setScore(score.acquireScore);
        workers.add(worker);
      }
    }

    for (AgentWorker worker : workers) {
      agentWorkPool.submit(worker);
    }
  }

  private <T> T withRetry(String description, Callable<T> callback) {
    return Failsafe.with(RETRY_POLICY)
        .onRetriesExceeded(
            failure -> {
              throw new ExcessiveDynoFailureRetries(description, failure);
            })
        .get(callback);
  }

  private void withRetry(String description, Runnable callback) {
    Failsafe.with(RETRY_POLICY)
        .onRetriesExceeded(
            failure -> {
              throw new ExcessiveDynoFailureRetries(description, failure);
            })
        .run(callback::run);
  }

  private static class AgentWorker implements Runnable {
    private final Agent agent;
    private final AgentExecution agentExecution;
    private final ExecutionInstrumentation executionInstrumentation;
    private final DynoClusteredSortAgentScheduler scheduler;
    private String acquireScore;

    AgentWorker(
        Agent agent,
        AgentExecution agentExecution,
        ExecutionInstrumentation executionInstrumentation,
        DynoClusteredSortAgentScheduler scheduler) {
      this.agent = agent;
      this.agentExecution = agentExecution;
      this.executionInstrumentation = executionInstrumentation;
      this.scheduler = scheduler;
    }

    public void setScore(String score) {
      acquireScore = score;
    }

    @Override
    public void run() {
      assert acquireScore != null;

      if (agentExecution instanceof CachingAgent.CacheExecution) {
        runAsCache();
      } else {
        runAsSideEffect();
      }
    }

    private void runAsCache() {
      if (!(agentExecution instanceof CachingAgent.CacheExecution)) {
        // If this exception is hit, there's a bug in the main run() method. Definitely shouldn't
        // happen.
        throw new IllegalStateException("Agent execution must be a CacheExecution to runAsCache");
      }
      CachingAgent.CacheExecution agentExecution =
          (CachingAgent.CacheExecution) this.agentExecution;

      CacheResult result = null;
      Status status = Status.FAILURE;
      try {
        executionInstrumentation.executionStarted(agent);
        long startTime = System.nanoTime();
        result = agentExecution.executeAgentWithoutStore(agent);
        executionInstrumentation.executionCompleted(
            agent, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime));
        status = Status.SUCCESS;
      } catch (Throwable cause) {
        executionInstrumentation.executionFailed(agent, cause);
      } finally {
        // Regardless of success or failure, we need to try and release this agent. If the release
        // is successful (we
        // own this agent), and a result was created, we can store it.
        scheduler.runningAgents.ifPresent(Semaphore::release);
        if (scheduler.conditionalReleaseAgent(agent, acquireScore, status) != null
            && result != null) {
          agentExecution.storeAgentResult(agent, result);
        }
      }
    }

    private void runAsSideEffect() {
      Status status = Status.FAILURE;
      try {
        executionInstrumentation.executionStarted(agent);
        long startTime = System.nanoTime();
        agentExecution.executeAgent(agent);
        executionInstrumentation.executionCompleted(
            agent, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime));
        status = Status.SUCCESS;
      } catch (Throwable cause) {
        executionInstrumentation.executionFailed(agent, cause);
      } finally {
        scheduler.runningAgents.ifPresent(Semaphore::release);
        scheduler.conditionalReleaseAgent(agent, acquireScore, status);
      }
    }
  }

  private static class ScoreTuple {
    private final String acquireScore;
    private final String releaseScore;

    public ScoreTuple(String acquireScore, String releaseScore) {
      this.acquireScore = acquireScore;
      this.releaseScore = releaseScore;
    }
  }
}
