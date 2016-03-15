/*
 * Copyright 2016 Netflix, Inc.
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

package com.netflix.spinnaker.orca.listeners;

import java.util.Arrays;
import java.util.List;
import com.google.common.collect.Lists;
import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.pipeline.model.Execution;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import com.netflix.spinnaker.orca.pipeline.model.Task;

/**
 * Listener that delegates to other listeners in "onion order".
 */
public class CompositeStageListener implements StageListener {

  private final List<StageListener> delegates;
  private final List<StageListener> reversed;

  public CompositeStageListener(StageListener... delegates) {
    this.delegates = Arrays.asList(delegates);
    this.reversed = Lists.reverse(this.delegates);
  }

  @Override
  public <T extends Execution<T>> void beforeTask(Persister persister, Stage<T> stage, Task task) {
    delegates.forEach(it -> it.beforeTask(persister, stage, task));
  }

  @Override
  public <T extends Execution<T>> void beforeStage(Persister persister, Stage<T> stage) {
    delegates.forEach(it -> it.beforeStage(persister, stage));
  }

  @Override
  public <T extends Execution<T>> void afterTask(Persister persister, Stage<T> stage, Task task, ExecutionStatus executionStatus, boolean wasSuccessful) {
    reversed.forEach(it -> it.afterTask(persister, stage, task, executionStatus, wasSuccessful));
  }

  @Override
  public <T extends Execution<T>> void afterStage(Persister persister, Stage<T> stage) {
    reversed.forEach(it -> it.afterStage(persister, stage));
  }
}
