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

import java.util.List;
import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.listeners.DefaultPersister;
import com.netflix.spinnaker.orca.listeners.Persister;
import com.netflix.spinnaker.orca.listeners.StageListener;
import com.netflix.spinnaker.orca.pipeline.model.Execution;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;

/**
 * This listener translates events coming from the nu-orca queueing system to
 * the old Spring Batch style listeners. Once we're running full-time on the
 * queue we should simplify things.
 */
public final class StageListenerAdapter implements ApplicationListener<ExecutionEvent> {

  private final StageListener delegate;
  private final ExecutionRepository repository;
  private final Persister persister;

  @Autowired
  public StageListenerAdapter(
    StageListener delegate,
    ExecutionRepository repository
  ) {
    this.delegate = delegate;
    this.repository = repository;
    this.persister = new DefaultPersister(this.repository);
  }

  @Override public void onApplicationEvent(ExecutionEvent event) {
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
    Execution execution = retrieve(event);
    List<Stage> stages = execution.getStages();
    stages
      .stream()
      .filter(it -> it.getId().equals(event.getStageId()))
      .findFirst()
      .ifPresent(stage -> delegate.beforeStage(persister, stage));
  }

  private void onStageComplete(StageComplete event) {
    Execution execution = retrieve(event);
    List<Stage> stages = execution.getStages();
    stages
      .stream()
      .filter(it -> it.getId().equals(event.getStageId()))
      .findFirst()
      .ifPresent(stage -> delegate.afterStage(persister, stage));
  }

  private void onTaskStarted(TaskStarted event) {
    Execution execution = retrieve(event);
    List<Stage> stages = execution.getStages();
    stages
      .stream()
      .filter(it -> it.getId().equals(event.getStageId()))
      .findFirst()
      .ifPresent(stage ->
        delegate.beforeTask(
          persister,
          stage,
          stage.getTasks().stream().filter(it -> it.getId().equals(event.getTaskId())).findFirst().get()
        )
      );
  }

  private void onTaskComplete(TaskComplete event) {
    Execution execution = retrieve(event);
    List<Stage> stages = execution.getStages();
    ExecutionStatus status = event.getStatus();
    stages
      .stream()
      .filter(it -> it.getId().equals(event.getStageId()))
      .findFirst()
      .ifPresent(stage ->
        delegate.afterTask(persister,
          stage,
          stage.getTasks().stream().filter(it -> it.getId().equals(event.getTaskId())).findFirst().get(),
          status,
          // TODO: not sure if status.isSuccessful covers all the weird cases here
          status.isSuccessful()
        )
      );
  }

  private Execution retrieve(ExecutionEvent event) {
    return repository.retrieve(event.getExecutionType(), event.getExecutionId());
  }
}
