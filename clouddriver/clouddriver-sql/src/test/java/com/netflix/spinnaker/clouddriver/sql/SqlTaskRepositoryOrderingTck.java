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
import com.netflix.spinnaker.clouddriver.core.ClouddriverHostname;
import com.netflix.spinnaker.clouddriver.data.task.Status;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.config.ConnectionPools;
import com.netflix.spinnaker.kork.sql.test.SqlTestUtil;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.jooq.DSLContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Ordering and concurrency behaviour of {@link SqlTaskRepository} that the shared {@code
 * TaskRepositoryTck} can't express: pod clock skew, writes that share a millisecond, concurrent
 * writers, and rows written before the per-task sequence existed. Findings S1–S3, S5 and S7 in
 * spinnaker/spinnaker#8109.
 */
abstract class SqlTaskRepositoryOrderingTck {

  private static final int ATTEMPTS = 20;

  private SqlTestUtil.TestDatabase database;
  private ExecutorService executor;

  protected abstract SqlTestUtil.TestDatabase initDatabase();

  @BeforeEach
  void setup() {
    database = initDatabase();
    executor = Executors.newFixedThreadPool(8);
  }

  @AfterEach
  void cleanup() {
    executor.shutdownNow();
    if (database != null) {
      SqlTestUtil.cleanupDb(database.context);
      database.close();
    }
  }

  /**
   * S1: a retry is visible even when the retrying pod's clock lags the pod that failed the task.
   */
  @Test
  void retryOnAPodWithALaggingClockIsVisible() {
    Instant now = Instant.now();
    SqlTaskRepository podA = repository(Clock.fixed(now, ZoneOffset.UTC));
    SqlTaskRepository podB =
        repository(Clock.fixed(now.minus(Duration.ofMillis(50)), ZoneOffset.UTC));

    for (int i = 0; i < ATTEMPTS; i++) {
      Task task = podA.create("ORCHESTRATION", "Initializing");
      task.fail(true);

      Task onPodB = podB.get(task.getId());
      onPodB.retry();
      onPodB.updateStatus("ORCHESTRATION", "Re-initializing");

      Status status = podA.get(task.getId()).getStatus();
      assertThat(status.isCompleted()).isFalse();
      assertThat(status.getStatus()).isEqualTo("Re-initializing");
    }
  }

  /** S2: completed tasks aren't listed as running when their states share a millisecond. */
  @Test
  void completedTasksAreNotListedAsRunningWhenTheirStatesShareAMillisecond() {
    SqlTaskRepository repository = repository(Clock.fixed(Instant.now(), ZoneOffset.UTC));

    Set<String> completed = new HashSet<>();
    for (int i = 0; i < ATTEMPTS; i++) {
      Task task = repository.create("ORCHESTRATION", "Initializing");
      task.updateStatus("ORCHESTRATION", "Orchestration completed.");
      task.complete();
      completed.add(task.getId());
    }

    assertThat(repository.list()).extracting(Task::getId).doesNotContainAnyElementsOf(completed);
    assertThat(repository.listByThisInstance())
        .extracting(Task::getId)
        .doesNotContainAnyElementsOf(completed);
  }

  /**
   * S3: history order doesn't depend on how row IDs sort. The completion row is given an ID that
   * sorts first, as a ULID generated in the same millisecond does about half the time.
   */
  @Test
  void historyOrderDoesNotDependOnRowIds() {
    SqlTaskRepository repository = repository(Clock.fixed(Instant.now(), ZoneOffset.UTC));

    for (int i = 0; i < ATTEMPTS; i++) {
      Task task = repository.create("ORCHESTRATION", "Initializing");
      task.updateStatus("ORCHESTRATION", "Orchestration completed.");
      task.complete();
      database
          .context
          .update(table("task_states"))
          .set(field("id"), String.format("%026d", i))
          .where(field("task_id").eq(task.getId()).and(field("state").eq("COMPLETED")))
          .execute();

      Task reloaded = repository.get(task.getId());
      assertThat(reloaded.getStatus().isCompleted()).isTrue();
      assertThat(reloaded.getHistory())
          .extracting(Status::getStatus)
          .containsExactly("Initializing", "Orchestration completed.", "Orchestration completed.");
      assertThat(reloaded.getHistory()).last().extracting(Status::isCompleted).isEqualTo(true);
    }
  }

  /** S5: a status update racing a completion can't reopen the completed task. */
  @Test
  void concurrentUpdatesCannotReopenACompletedTask() throws Exception {
    SqlTaskRepository repository = repository(Clock.systemUTC());

    for (int i = 0; i < ATTEMPTS; i++) {
      String taskId = repository.create("ORCHESTRATION", "Initializing").getId();
      Future<?> completer = executor.submit(() -> repository.get(taskId).complete());
      Future<?> updater =
          executor.submit(
              () -> {
                Task task = repository.get(taskId);
                for (int update = 0; update < 5; update++) {
                  try {
                    task.updateStatus("ORCHESTRATION", "Update " + update);
                  } catch (IllegalStateException alreadyCompleted) {
                    return;
                  }
                }
              });
      completer.get();
      updater.get();

      assertThat(repository.get(taskId).getStatus().isCompleted()).isTrue();
      assertThat(repository.list()).extracting(Task::getId).doesNotContain(taskId);
    }
  }

  /** S7: concurrent creates with the same client request ID share one task. */
  @Test
  void concurrentCreatesWithTheSameRequestIdShareOneTask() throws Exception {
    SqlTaskRepository repository = repository(Clock.systemUTC());

    for (int i = 0; i < ATTEMPTS; i++) {
      String requestId = "request-" + i;
      List<Callable<String>> creates =
          IntStream.range(0, 8)
              .mapToObj(
                  n ->
                      (Callable<String>)
                          () ->
                              repository.create("ORCHESTRATION", "Initializing", requestId).getId())
              .collect(Collectors.toList());

      Set<String> taskIds = new HashSet<>();
      for (Future<String> created : executor.invokeAll(creates)) {
        taskIds.add(created.get());
      }

      assertThat(taskIds).hasSize(1);
      assertThat(repository.getByClientRequestId(requestId).getId()).isIn(taskIds);
      assertThat(database.context.fetchCount(table("tasks"), field("request_id").eq(requestId)))
          .isEqualTo(1);
    }
  }

  /**
   * Tasks written before this change have no {@code seq}/{@code current_state}. They are still
   * read, listed and updated correctly, and their first new write records their current state.
   */
  @Test
  void tasksWrittenBeforeTheSequenceColumnsStillWork() {
    SqlTaskRepository repository = repository(Clock.systemUTC());
    long now = System.currentTimeMillis();
    insertPreUpgradeTask("recent", now - Duration.ofHours(1).toMillis());
    insertPreUpgradeTask("old", now - Duration.ofDays(2).toMillis());

    assertThat(repository.get("recent").getStatus().getStatus()).isEqualTo("Second");
    // Only recent pre-upgrade tasks can still be running.
    assertThat(repository.list()).extracting(Task::getId).contains("recent").doesNotContain("old");
    assertThat(repository.listByThisInstance()).extracting(Task::getId).contains("recent");

    Task task = repository.get("recent");
    task.updateStatus("ORCHESTRATION", "Third");
    task.complete();

    Task reloaded = repository.get("recent");
    assertThat(reloaded.getStatus().isCompleted()).isTrue();
    assertThat(reloaded.getHistory())
        .extracting(Status::getStatus)
        .containsExactly("First", "Second", "Third", "Third");
    assertThat(reloaded.getHistory()).last().extracting(Status::isCompleted).isEqualTo(true);
    assertThat(repository.list()).extracting(Task::getId).doesNotContain("recent");
    assertThat(
            database
                .context
                .select(field("current_state"))
                .from(table("tasks"))
                .where(field("id").eq("recent"))
                .fetchOne(0, String.class))
        .isEqualTo("COMPLETED");
  }

  private void insertPreUpgradeTask(String id, long createdAt) {
    DSLContext ctx = database.context;
    ctx.insertInto(table("tasks"))
        .columns(
            field("id"),
            field("request_id"),
            field("owner_id"),
            field("created_at"),
            field("saga_ids"))
        .values(id, "request-" + id, ClouddriverHostname.ID, createdAt, "[]")
        .execute();
    insertPreUpgradeState(id + "-1", id, createdAt, "First");
    insertPreUpgradeState(id + "-2", id, createdAt + 1, "Second");
  }

  private void insertPreUpgradeState(String id, String taskId, long createdAt, String status) {
    database
        .context
        .insertInto(table("task_states"))
        .columns(
            field("id"),
            field("task_id"),
            field("created_at"),
            field("state"),
            field("phase"),
            field("status"))
        .values(id, taskId, createdAt, "STARTED", "ORCHESTRATION", status)
        .execute();
  }

  private SqlTaskRepository repository(Clock clock) {
    return new SqlTaskRepository(
        database.context, new ObjectMapper(), clock, ConnectionPools.TASKS.getValue());
  }
}
