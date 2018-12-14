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
import com.netflix.spinnaker.orca.exceptions.DefaultExceptionHandler;
import com.netflix.spinnaker.orca.exceptions.ExceptionHandler;
import com.netflix.spinnaker.orca.locks.LockFailureException;
import com.netflix.spinnaker.orca.locks.LockManager;
import com.netflix.spinnaker.orca.locks.LockContext;
import com.netflix.spinnaker.orca.locks.LockingConfigurationProperties;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Component
public class AcquireLockTask implements Task {

  private final LockManager lockManager;
  private final LockingConfigurationProperties lockingConfigurationProperties;

  @Autowired
  public AcquireLockTask(LockManager lockManager,
                         LockingConfigurationProperties lockingConfigurationProperties) {
    this.lockManager = lockManager;
    this.lockingConfigurationProperties = lockingConfigurationProperties;
  }

  @Nonnull
  @Override
  public TaskResult execute(@Nonnull Stage stage) {
    LockContext lock = stage.mapTo("/lock", LockContext.LockContextBuilder.class).withStage(stage).build();
    try {
      lockManager.acquireLock(lock.getLockName(), lock.getLockValue(), lock.getLockHolder(), lockingConfigurationProperties.getTtlSeconds());
      return new TaskResult(ExecutionStatus.SUCCEEDED, Collections.singletonMap("lock", lock));
    } catch (LockFailureException lfe) {
      Map<String, Object> resultContext = new HashMap<>();
      ExceptionHandler.Response exResult = new DefaultExceptionHandler().handle("acquireLock", lfe);
      exResult.getDetails().put("lockName", lfe.getLockName());
      exResult.getDetails().put("currentLockValue", lfe.getCurrentLockValue());
      resultContext.put("exception", exResult);

      //Changes lock acquisition failure to stop the current branch, and fail
      // the pipeline when other branches complete. If one operation in parallel
      // is unable to acquire a lock, this would mean other operations would
      // complete normally (if they are able to lock) before the overall execution
      // fails.
      //
      //This is preferable to one operation failing to acquire a lock leaving other
      // operations in a partially completed state. Additionally this makes the stage
      // restart story a bit more sensible - we can restart the failed Acquire Lock
      // stage and the pipeline will proceed, and we haven't failed a bunch of other
      // stages halfway through so the pipeline will proceed for any downstream join
      // points.
      resultContext.put("completeOtherBranchesThenFail", true);
      return new TaskResult(ExecutionStatus.STOPPED, resultContext);
    }
  }
}
