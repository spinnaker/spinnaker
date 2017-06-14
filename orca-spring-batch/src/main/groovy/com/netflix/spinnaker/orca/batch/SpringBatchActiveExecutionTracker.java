/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.orca.batch;

import java.util.*;
import java.util.stream.Collectors;
import com.google.common.collect.ImmutableMap;
import com.netflix.spinnaker.orca.ActiveExecutionTracker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.launch.NoSuchJobException;
import org.springframework.batch.core.launch.NoSuchJobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import redis.clients.jedis.Jedis;
import redis.clients.util.Pool;
import rx.Observable;
import static java.lang.String.format;
import static java.util.Collections.*;
import static java.util.concurrent.TimeUnit.DAYS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.stream.Collectors.toCollection;
import static org.springframework.batch.support.PropertiesConverter.stringToProperties;

/**
 * Tracks active executions running on _this_ Orca instance and can return a
 * map with details of active executions running on all instances.
 * <p>
 * Orca instances are assumed to have become inactive if they do not update
 * their executions at least every {@link #COUNT_TTL_SECONDS}.
 */
@Deprecated
@Component
@Slf4j
public class SpringBatchActiveExecutionTracker implements ActiveExecutionTracker {

  /**
   * Frequency the tracker updates details of executions running on _this_
   * instance.
   */
  private static final long FREQUENCY_MS = 60_000; // 1 minute

  /**
   * Redis key for a list of Orca instances.
   */
  static final String KEY_INSTANCES = "active_instances";

  /**
   * Redis key template for the expiring token set by each Orca instance to
   * indicate they are active.
   */
  static final String KEY_INSTANCE_TOKEN = "active_executions:%s";

  /**
   * Redis key template for the set of execution details for each Orca instance.
   */
  static final String KEY_INSTANCE_EXECUTIONS = "active_executions:%s:executions";

  /**
   * Duration after which an Orca instance is assumed to be inactive if it has
   * not updated its active executions.
   */
  static final int COUNT_TTL_SECONDS = (int) MILLISECONDS.toSeconds(FREQUENCY_MS * 5);

  /**
   * Duration after which any zombie executions from an inactive instance are
   * removed.
   */
  static final int EXECUTIONS_TTL_SECONDS = (int) DAYS.toSeconds(7);

  private final JobOperator jobOperator;
  private final String currentInstance;
  private final Pool<Jedis> jedisPool;

  @Autowired
  public SpringBatchActiveExecutionTracker(JobOperator jobOperator,
                                           String currentInstance,
                                           Pool<Jedis> jedisPool) {
    this.jobOperator = jobOperator;
    this.currentInstance = currentInstance;
    this.jedisPool = jedisPool;
  }

  /**
   * @return map of instance ids to details of active executions.
   */
  @Override public Map<String, OrcaInstance> activeExecutionsByInstance() {
    return Observable
      .from(knownInstances())
      .map(instance ->
        singletonMap(instance, activeExecutionsFor(instance))
      )
      .reduce(this::reduce)
      .toBlocking()
      .single();
  }

  /**
   * @param instance an instance id.
   * @return `true` if the instance has reported executions in the
   * last {@link #COUNT_TTL_SECONDS}, `false` otherwise.
   */
  @Override public boolean isActiveInstance(String instance) {
    try (Jedis jedis = jedisPool.getResource()) {
      return jedis.exists(tokenKeyFor(instance));
    }
  }

  /**
   * Records the details of active executions running on _this_ instance.
   */
  @Scheduled(fixedDelay = FREQUENCY_MS)
  public void recordRunningExecutions() {
    log.debug("Checking for running executions");
    Observable
      .from(jobOperator.getJobNames())
      .flatMapIterable(this::runningExecutions)
      .toList()
      .onErrorResumeNext(Observable.just(emptyList()))
      .subscribe(this::recordExecutions);
  }

  /**
   * @return list of known Orca instances.
   */
  private Set<String> knownInstances() {
    try (Jedis jedis = jedisPool.getResource()) {
      return jedis.smembers(KEY_INSTANCES);
    }
  }

  /**
   * Retrieves executions running on `instance` and removes `instance` from the
   * {@link #KEY_INSTANCES} set if its token has expired and it had no
   * executions still running.
   *
   * @param instance an Orca instance id.
   * @return details of executions running on `instance`.
   */
  private OrcaInstance activeExecutionsFor(String instance) {
    try (Jedis jedis = jedisPool.getResource()) {
      Optional<Integer> count = Optional
        .ofNullable(jedis.get(tokenKeyFor(instance)))
        .map(Integer::decode);
      long executions = jedis.scard(executionsKeyFor(instance));
      if (count.isPresent()) {
        if (executions > 0) {
          // active instance running executions
          return new OrcaInstance(false, (int) executions, readExecutions(jedis, instance));
        } else {
          // either idle or legacy instance that's only recording count
          return new OrcaInstance(false, count.get(), emptySortedSet());
        }
      } else {
        if (executions == 0) {
          jedis.srem(KEY_INSTANCES, instance);
          jedis.del(executionsKeyFor(instance));
          // terminated instance that had drained all work
          return new OrcaInstance(true, 0, emptySortedSet());
        } else {
          // instance that terminated while still running work
          return new OrcaInstance(true, (int) executions, readExecutions(jedis, instance));
        }
      }
    }
  }

  private SortedSet<ExecutionRecord> readExecutions(Jedis jedis, String instance) {
    return jedis
      .smembers(executionsKeyFor(instance))
      .stream()
      .map(ExecutionRecord::valueOf)
      .collect(toCollection(TreeSet::new));
  }

  /**
   * @param executions the executions currently running on _this_ instance.
   */
  private void recordExecutions(Collection<ExecutionRecord> executions) {
    log.info("Currently running {} executions", executions.size());
    try (Jedis jedis = jedisPool.getResource()) {
      jedis.sadd(KEY_INSTANCES, currentInstance);
      jedis.setex(tokenKeyFor(currentInstance), COUNT_TTL_SECONDS, String.valueOf(executions.size()));
      jedis.del(executionsKeyFor(currentInstance));
      if (!executions.isEmpty()) {
        jedis.sadd(executionsKeyFor(currentInstance), toStrings(executions));
        jedis.expire(executionsKeyFor(currentInstance), EXECUTIONS_TTL_SECONDS);
      }
    }
  }

  private String[] toStrings(Collection<ExecutionRecord> executions) {
    return executions
      .stream()
      .map(ExecutionRecord::toString)
      .collect(Collectors.toList())
      .toArray(new String[executions.size()]);
  }

  /**
   * @param name a Spring Batch job name.
   * @return details of currently running pipelines and tasks.
   */
  private Set<ExecutionRecord> runningExecutions(String name) {
    try {
      Set<ExecutionRecord> result = new HashSet<>();
      Set<Long> executions = jobOperator.getRunningExecutions(name);
      for (Long id : executions) {
        String paramString = jobOperator.getParameters(id);
        Properties params = stringToProperties(paramString);
        String application = params.getProperty("application");
        String pipelineId = params.getProperty("pipeline");
        if (pipelineId != null) {
          result.add(new ExecutionRecord(application, "pipeline", pipelineId));
        } else {
          result.add(new ExecutionRecord(application, "task", params.getProperty("orchestration")));
        }
      }
      return result;
    } catch (NoSuchJobException | NoSuchJobExecutionException e) {
      // this should be an impossible condition as the job name was given to us
      // by the JobOperator in the first place
      return emptySet();
    }
  }

  /**
   * @param instance an Orca instance id.
   * @return Redis key to store an expiring token for `instance`.
   */
  static String tokenKeyFor(String instance) {
    return format(KEY_INSTANCE_TOKEN, instance);
  }

  static String executionsKeyFor(String instance) {
    return format(KEY_INSTANCE_EXECUTIONS, instance);
  }

  /**
   * @return the merge of `a` and `b`.
   */
  private <K, V> Map<K, V> reduce(Map<K, V> a, Map<K, V> b) {
    return ImmutableMap
      .<K, V>builder()
      .putAll(a)
      .putAll(b)
      .build();
  }
}
