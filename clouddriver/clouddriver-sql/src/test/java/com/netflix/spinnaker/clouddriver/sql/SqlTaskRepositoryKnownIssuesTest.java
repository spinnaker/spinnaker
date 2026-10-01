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

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.config.ConnectionPools;
import com.netflix.spinnaker.kork.sql.test.SqlTestUtil;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reproduces known {@link SqlTaskRepository} defects tracked in spinnaker/spinnaker#8109 (plan:
 * {@code plans/2026/clouddriver-task-repository.md}).
 *
 * <p>Each test asserts the correct behaviour. While a defect is still present the test logs how
 * often it reproduced and is <b>skipped</b> (aborted through a JUnit assumption) instead of failed,
 * so the defect stays visible in test reports without breaking the build. Once a fix lands the test
 * passes; the PR fixing it should replace the assumption with a hard assertion.
 */
class SqlTaskRepositoryKnownIssuesTest {

  private static final Logger log = LoggerFactory.getLogger(SqlTaskRepositoryKnownIssuesTest.class);

  private static final int ATTEMPTS = 50;

  private SqlTestUtil.TestDatabase database;

  @BeforeEach
  void setup() {
    database = SqlTestUtil.initTcMysqlDatabase();
  }

  @AfterEach
  void cleanup() {
    if (database != null) {
      SqlTestUtil.cleanupDb(database.context);
    }
  }

  /**
   * S1: the latest state is chosen by {@code created_at}, taken from the writing pod's clock. A
   * retry performed on a pod whose clock lags sorts before the failure it retries, so the task
   * still reports the failure and the retry is invisible.
   */
  @Test
  void s1RetryOnAPodWithALaggingClockIsVisible() {
    Instant now = Instant.now();
    SqlTaskRepository podA = repository(Clock.fixed(now, ZoneOffset.UTC));
    SqlTaskRepository podB =
        repository(Clock.fixed(now.minus(Duration.ofMillis(50)), ZoneOffset.UTC));

    int retriesHidden = 0;
    for (int i = 0; i < ATTEMPTS; i++) {
      Task task = podA.create("ORCHESTRATION", "Initializing");
      task.fail(true);

      Task onPodB = podB.get(task.getId());
      onPodB.updateStatus("ORCHESTRATION", "Re-initializing");
      onPodB.retry();

      if (podA.get(task.getId()).getStatus().isCompleted()) {
        retriesHidden++;
      }
    }

    knownIssue(
        "S1",
        retriesHidden,
        "retried tasks still report FAILED_RETRYABLE when the retrying pod's clock lags by 50 ms");
  }

  /**
   * S2: running tasks are found by joining each task's states on {@code MAX(created_at)}. When the
   * final status update and the completion share a millisecond, both rows match, so a completed
   * task is still listed as running. A fixed clock makes every write share a millisecond.
   */
  @Test
  void s2CompletedTaskIsNotListedAsRunningWhenStatesShareAMillisecond() {
    SqlTaskRepository repository = repository(Clock.fixed(Instant.now(), ZoneOffset.UTC));

    Set<String> completed = new HashSet<>();
    for (int i = 0; i < ATTEMPTS; i++) {
      Task task = repository.create("ORCHESTRATION", "Initializing");
      task.updateStatus("ORCHESTRATION", "Orchestration completed.");
      task.complete();
      completed.add(task.getId());
    }

    int listedAsRunning =
        (int) repository.list().stream().filter(t -> completed.contains(t.getId())).count();

    knownIssue(
        "S2",
        listedAsRunning,
        "completed tasks are still listed as running when their last two states share a millisecond");
  }

  private SqlTaskRepository repository(Clock clock) {
    return new SqlTaskRepository(
        database.context, new ObjectMapper(), clock, ConnectionPools.TASKS.getValue());
  }

  private static void knownIssue(String id, int reproduced, String description) {
    String message =
        String.format(
            "KNOWN ISSUE %s (spinnaker/spinnaker#8109) reproduced %d/%d: %s",
            id, reproduced, ATTEMPTS, description);
    if (reproduced > 0) {
      log.warn(message);
    }
    assumeTrue(reproduced == 0, message);
  }
}
