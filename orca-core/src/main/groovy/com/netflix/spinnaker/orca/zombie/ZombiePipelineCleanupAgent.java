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

package com.netflix.spinnaker.orca.zombie;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import com.netflix.spinnaker.orca.ActiveExecutionTracker;
import com.netflix.spinnaker.orca.pipeline.model.Pipeline;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import rx.Observable;
import static com.netflix.spinnaker.orca.ExecutionStatus.CANCELED;
import static com.netflix.spinnaker.orca.ExecutionStatus.RUNNING;
import static com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionEngine.v2;
import static java.lang.String.format;
import static java.time.Clock.systemDefaultZone;
import static java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME;
import static rx.Observable.just;

/**
 * This poller cancels any pipelines that were still running but their Orca
 * instance has died.
 */
@Component
@ConditionalOnExpression("${pollers.zombiePipeline.enabled:true}")
@Slf4j
@Deprecated
public class ZombiePipelineCleanupAgent {

  /**
   * How often the agent runs.
   */
  private static final long FREQUENCY_MS = 21_600_000; // 6 hours

  /**
   * Delay before running the agent the first time.
   */
  private static final long INITIAL_DELAY_MS = 300_000; // 5 minutes

  private final ActiveExecutionTracker activeExecutionTracker;
  private final ExecutionRepository repository;
  private final Clock clock;
  private final Lock lock;
  private final String currentInstanceId;

  @Autowired
  public ZombiePipelineCleanupAgent(ActiveExecutionTracker activeExecutionTracker,
                                    ExecutionRepository repository,
                                    Lock lock,
                                    String currentInstanceId) {
    this(
      activeExecutionTracker,
      repository,
      lock,
      currentInstanceId,
      systemDefaultZone()
    );
  }

  ZombiePipelineCleanupAgent(ActiveExecutionTracker activeExecutionTracker,
                             ExecutionRepository repository,
                             Lock lock,
                             String currentInstanceId,
                             Clock clock) {
    this.activeExecutionTracker = activeExecutionTracker;
    this.repository = repository;
    this.clock = clock;
    this.currentInstanceId = currentInstanceId;
    this.lock = lock;
  }

  @Scheduled(fixedDelay = FREQUENCY_MS, initialDelay = INITIAL_DELAY_MS)
  public void slayZombies() {
    lock
      .withLock(
        format("%s.%d", currentInstanceId, clock.millis()),
        this::findZombies,
        this::slayZombie
      );
  }

  public void slayIfZombie(Pipeline pipeline, boolean force) {
    just(pipeline)
      .filter(this::isIncomplete)
      .filter(p -> force || isRunningOnZombieInstance(p))
      .subscribe(this::slayZombie);
  }

  private Observable<Pipeline> findZombies() {
    log.info("Starting sweep for zombie pipelines...");
    return repository
      .retrievePipelines()
      .filter(this::isIncomplete)
      .filter(this::isV2)
      .filter(this::isRunningOnZombieInstance)
      .doOnCompleted(() -> log.info("Zombie pipeline sweep completed."));
  }

  private void slayZombie(Pipeline pipeline) {
    log.warn(
      "Canceling zombie pipeline {} for {} started at {} with id {}",
      pipeline.getName(),
      pipeline.getApplication(),
      formatStartTime(pipeline),
      pipeline.getId()
    );
    repository.cancel(pipeline.getId(), "Spinnaker", "The pipeline appeared to be stalled.");
    repository.updateStatus(pipeline.getId(), CANCELED);
    Observable
      .from(pipeline.getStages())
      .filter(stage -> stage.getStatus() == RUNNING)
      .subscribe(stage -> {
        stage.setStatus(CANCELED);
        repository.storeStage(stage);
      });
  }

  private String formatStartTime(Pipeline pipeline) {
    if (pipeline.getStartTime() == null) {
      return null;
    } else {
      return ISO_LOCAL_DATE_TIME
        .format(Instant.ofEpochMilli(pipeline.getStartTime())
          .atZone(ZoneId.systemDefault()));
    }
  }

  private boolean isRunningOnZombieInstance(Pipeline pipeline) {
    return !activeExecutionTracker.isActiveInstance(pipeline.getExecutingInstance());
  }

  private boolean isIncomplete(Pipeline pipeline) {
    return !pipeline.getStatus().isComplete();
  }

  private boolean isV2(Pipeline pipeline) {
    return pipeline.getExecutionEngine() == v2;
  }
}
