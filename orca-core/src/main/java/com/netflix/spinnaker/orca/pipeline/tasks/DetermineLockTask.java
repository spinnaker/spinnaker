/*
 * Copyright 2018 Netflix, Inc.
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

package com.netflix.spinnaker.orca.pipeline.tasks;

import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.Task;
import com.netflix.spinnaker.orca.TaskResult;
import com.netflix.spinnaker.orca.locks.LockContext;
import com.netflix.spinnaker.orca.locks.LockingConfigurationProperties;
import com.netflix.spinnaker.orca.pipeline.AcquireLockStage;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import com.netflix.spinnaker.orca.pipeline.util.StageNavigator;
import net.logstash.logback.argument.StructuredArguments;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.Optional;

@Component
public class DetermineLockTask implements Task {

  private final StageNavigator stageNavigator;
  private final LockingConfigurationProperties lockingConfigurationProperties;
  private final Logger log = LoggerFactory.getLogger(getClass());

  @Autowired
  public DetermineLockTask(StageNavigator stageNavigator,
                           LockingConfigurationProperties lockingConfigurationProperties) {
    this.stageNavigator = stageNavigator;
    this.lockingConfigurationProperties = lockingConfigurationProperties;
  }

  @Nonnull
  @Override
  public TaskResult execute(@Nonnull Stage stage) {
    Optional<StageNavigator.Result> lockStageResult = stageNavigator
      .ancestors(stage)
      .stream()
      .filter(
        r -> r.getStageBuilder() instanceof AcquireLockStage)
      .filter(r ->
        stage.getParentStageId() == null ? r.getStage().getParentStageId() == null :
          stage.getParentStageId().equals(r.getStage().getParentStageId()))
      .findFirst();

    try {
      final LockContext lockContext;
      if (lockStageResult.isPresent()) {
        final Stage lockStage = lockStageResult.get().getStage();
        lockContext = lockStage.mapTo("/lock", LockContext.LockContextBuilder.class).withStage(lockStage).build();
      } else {
        lockContext = stage.mapTo("/lock", LockContext.LockContextBuilder.class).build();
      }

      return new TaskResult(ExecutionStatus.SUCCEEDED, Collections.singletonMap("lock", lockContext));
    } catch (Exception ex) {
      final boolean lockingEnabled = lockingConfigurationProperties.isEnabled();
      final boolean learningMode = lockingConfigurationProperties.isLearningMode();
      if (!lockingEnabled || learningMode) {
        log.debug("DetermineLockTask failed. Ignoring due to {} {}",
          StructuredArguments.kv("locking.enabled", lockingEnabled),
          StructuredArguments.kv("locking.learningMode", learningMode),
          ex);
        LockContext lc = new LockContext.LockContextBuilder("unknown", null, "unknown").withStage(stage).build();
        return new TaskResult(ExecutionStatus.SUCCEEDED, Collections.singletonMap("lock", lc));
      }
      throw new IllegalStateException("Unable to determine lock from context or previous lock stage", ex);
    }
  }
}
