/*
 * Copyright 2026 McIntosh.farm
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

package com.netflix.spinnaker.clouddriver.sql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spectator.api.NoopRegistry;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.config.ConnectionPools;
import com.netflix.spinnaker.config.SqlTaskCleanupAgentProperties;
import com.netflix.spinnaker.kork.sql.test.SqlTestUtil;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Which tasks {@link SqlTaskCleanupAgent} removes, by state and age (spinnaker/spinnaker#8109 S9).
 */
class SqlTaskCleanupAgentStatesTest {

  private SqlTestUtil.TestDatabase database;
  private final SqlTaskCleanupAgentProperties properties = new SqlTaskCleanupAgentProperties();

  @BeforeEach
  void setup() {
    database = SqlTestUtil.initTcMysqlDatabase();
  }

  @AfterEach
  void cleanup() {
    SqlTestUtil.cleanupDb(database.context);
    database.close();
  }

  @Test
  void removesRetryableFailuresOlderThanTheirTtl() {
    Task task = taskCreatedAgo(properties.getFailedRetryableTtlMs() + 1);
    task.fail(true);

    runCleanup();

    assertThat(exists(task)).isFalse();
    assertThat(childRows("task_states", task)).isZero();
  }

  @Test
  void keepsRetryableFailuresYoungerThanTheirTtl() {
    // Past the completed TTL, so only the longer retryable TTL keeps it.
    Task task = taskCreatedAgo(properties.getCompletedTtlMs() + 1);
    task.fail(true);

    runCleanup();

    assertThat(exists(task)).isTrue();
  }

  @Test
  void keepsRetryableFailuresThatWereRetried() {
    Task task = taskCreatedAgo(properties.getFailedRetryableTtlMs() + 1);
    task.fail(true);
    task.retry();

    runCleanup();

    assertThat(exists(task)).isTrue();
  }

  @Test
  void removesCompletedAndFailedTasksOlderThanTheirTtl() {
    Task completed = taskCreatedAgo(properties.getCompletedTtlMs() + 1);
    completed.complete();
    Task failed = taskCreatedAgo(properties.getCompletedTtlMs() + 1);
    failed.fail(false);

    runCleanup();

    assertThat(exists(completed)).isFalse();
    assertThat(exists(failed)).isFalse();
  }

  @Test
  void keepsRunningTasksHoweverOldTheyAre() {
    Task running = taskCreatedAgo(properties.getFailedRetryableTtlMs() * 2);

    runCleanup();

    assertThat(exists(running)).isTrue();
  }

  private Task taskCreatedAgo(long ageMs) {
    Clock created = Clock.fixed(Instant.now().minus(Duration.ofMillis(ageMs)), ZoneOffset.UTC);
    return new SqlTaskRepository(
            database.context, new ObjectMapper(), created, ConnectionPools.TASKS.getValue())
        .create("ORCHESTRATION", "Initializing");
  }

  private void runCleanup() {
    new SqlTaskCleanupAgent(database.context, Clock.systemUTC(), new NoopRegistry(), properties)
        .run();
  }

  private boolean exists(Task task) {
    return database.context.fetchCount(table("tasks"), field("id").eq(task.getId())) > 0;
  }

  private int childRows(String table, Task task) {
    return database.context.fetchCount(table(table), field("task_id").eq(task.getId()));
  }
}
