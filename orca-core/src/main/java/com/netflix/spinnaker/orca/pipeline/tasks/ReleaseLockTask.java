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

import com.netflix.spinnaker.orca.Task;
import com.netflix.spinnaker.orca.TaskResult;
import com.netflix.spinnaker.orca.locks.LockManager;
import com.netflix.spinnaker.orca.locks.LockContext;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;

@Component
public class ReleaseLockTask implements Task {

  private final LockManager lockManager;

  @Autowired
  public ReleaseLockTask(LockManager lockManager) {
    this.lockManager = lockManager;
  }

  @Nonnull
  @Override
  public TaskResult execute(@Nonnull Stage stage) {
    final LockContext lock = stage.mapTo("/lock", LockContext.LockContextBuilder.class).withStage(stage).build();
    lockManager.releaseLock(lock.getLockName(), lock.getLockValue(), lock.getLockHolder());
    return TaskResult.SUCCEEDED;
  }
}
