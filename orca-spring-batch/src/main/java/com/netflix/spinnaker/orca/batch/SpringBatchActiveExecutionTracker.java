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

import java.util.Map;
import java.util.Set;
import com.google.common.collect.ImmutableMap;
import com.netflix.spinnaker.orca.ActiveExecutionTracker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.launch.NoSuchJobException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import redis.clients.jedis.Jedis;
import redis.clients.util.Pool;
import rx.Observable;
import static java.lang.String.format;
import static java.util.Collections.singletonMap;
import static java.util.concurrent.TimeUnit.MINUTES;

/**
 * Tracks active executions running on _this_ Orca instance and can return a
 * map with a count of active executions running on all instances.
 * <p>
 * Orca instances are assumed to have become inactive if they do not update
 * their count at least every {@link #TTL_SECONDS}.
 */
@Component
@Slf4j
public class SpringBatchActiveExecutionTracker implements ActiveExecutionTracker {

  /**
   * Frequency the tracker updates its count of executions running on _this_
   * instance.
   */
  private static final long FREQUENCY_MS = 60_000; // 1 minute

  /**
   * Redis key for a list of Orca instances.
   */
  static final String KEY_INSTANCES = "active_instances";

  /**
   * Redis key template for the count of active executions for a given Orca
   * instance.
   */
  static final String KEY_PER_INSTANCE = "active_executions:%s";

  /**
   * Duration after which an Orca instance is assumed to be inactive if it has
   * not updated its active execution count.
   */
  static final int TTL_SECONDS = (int) MINUTES.toSeconds(30);

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
   * @return map of instance ids to count of active executions.
   */
  @Override public Map<String, Integer> activeExecutionsByInstance() {
    return Observable
      .from(activeInstances())
      .map(instance ->
        singletonMap(instance, activeExecutionsFor(instance))
      )
      .reduce(this::reduce)
      .toBlocking()
      .single();
  }

  /**
   * Records the count of active executions running on _this_ instance.
   */
  @Scheduled(fixedDelay = FREQUENCY_MS)
  public void countRunningExecutions() {
    log.debug("Checking for running executions");
    Observable
      .from(jobOperator.getJobNames())
      .map(this::runningExecutionCount)
      .reduce(0, (a, b) -> a + b)
      .subscribe(this::recordCount);
  }

  /**
   * @return list of known Orca instances.
   */
  private Set<String> activeInstances() {
    try (Jedis jedis = jedisPool.getResource()) {
      return jedis.smembers(KEY_INSTANCES);
    }
  }

  /**
   * Counts executions running on `instance` and removes `instance` from the
   * {@link #KEY_INSTANCES} set if its count has expired.
   *
   * @param instance an Orca instance id.
   * @return count of executions running on `instance`.
   */
  private int activeExecutionsFor(String instance) {
    try (Jedis jedis = jedisPool.getResource()) {
      String count = jedis.get(keyFor(instance));
      if (count == null) {
        jedis.srem(KEY_INSTANCES, instance);
        return 0;
      } else {
        return Integer.parseInt(count);
      }
    }
  }

  /**
   * @param count number of executions currently running on _this_ instance.
   */
  private void recordCount(int count) {
    log.info("Currently running {} executions", count);
    try (Jedis jedis = jedisPool.getResource()) {
      jedis.sadd(KEY_INSTANCES, currentInstance);
      jedis.setex(keyFor(currentInstance), TTL_SECONDS, String.valueOf(count));
    }
  }

  /**
   * @param name a Spring Batch job name.
   * @return number of currently running `JobExecution` instances.
   */
  private int runningExecutionCount(String name) {
    try {
      return jobOperator.getRunningExecutions(name).size();
    } catch (NoSuchJobException e) {
      // this should be an impossible condition as the job name was given to us
      // by the JobOperator in the first place
      return 0;
    }
  }

  /**
   * @param instance an Orca instnace id.
   * @return Redis key to store active execution count for `instance`.
   */
  static String keyFor(String instance) {
    return format(KEY_PER_INSTANCE, instance);
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
