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

package com.netflix.spinnaker.orca.events;

import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.listeners.StageListener;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;

/**
 * This listener translates events coming from the nu-orca queueing system to the old Spring Batch
 * style listeners. Once we're running full-time on the queue we should simplify things.
 */
public final class StageListenerAdapter implements ApplicationListener<ExecutionEvent> {
  private final StageListener delegate;

  @Autowired
  public StageListenerAdapter(StageListener delegate) {
    this.delegate = delegate;
  }

  @Override
  public void onApplicationEvent(@NotNull ExecutionEvent event) {
    if (event instanceof StageStarted) {
      onStageStarted((StageStarted) event);
    } else if (event instanceof StageComplete) {
      onStageComplete((StageComplete) event);
    } else if (event instanceof TaskStarted) {
      onTaskStarted((TaskStarted) event);
    } else if (event instanceof TaskComplete) {
      onTaskComplete((TaskComplete) event);
    }
  }

  private void onStageStarted(StageStarted event) {
    delegate.beforeStage(event.getStage());
  }

  private void onStageComplete(StageComplete event) {
    delegate.afterStage(event.getStage());
  }

  private void onTaskStarted(TaskStarted event) {
    delegate.beforeTask(event.getStage(), event.getTask());
  }

  private void onTaskComplete(TaskComplete event) {
    StageExecution stage = event.getStage();
    delegate.afterTask(stage, event.getTask(), stage.getStatus(), stage.getStatus().isSuccessful());
  }
}
