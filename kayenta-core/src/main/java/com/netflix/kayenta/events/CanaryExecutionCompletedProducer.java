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
 package com.netflix.kayenta.events;

import com.netflix.kayenta.canary.CanaryExecutionStatusResponse;
import com.netflix.kayenta.canary.ExecutionMapper;
import com.netflix.spinnaker.orca.events.ExecutionComplete;
import com.netflix.spinnaker.orca.pipeline.model.Execution;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class CanaryExecutionCompletedProducer implements ApplicationListener<ExecutionComplete> {

  private final ApplicationEventPublisher applicationEventPublisher;
  private final ExecutionRepository executionRepository;
  private final ExecutionMapper executionMapper;

  @Autowired
  public CanaryExecutionCompletedProducer(ApplicationEventPublisher applicationEventPublisher, ExecutionRepository executionRepository, ExecutionMapper executionMapper) {
    this.applicationEventPublisher = applicationEventPublisher;
    this.executionRepository = executionRepository;
    this.executionMapper = executionMapper;
  }

  @Override
  public void onApplicationEvent(ExecutionComplete event) {
    if (event.getExecutionType() != Execution.ExecutionType.PIPELINE) {
      return;
    }
    Execution execution = executionRepository.retrieve(Execution.ExecutionType.PIPELINE, event.getExecutionId());
    CanaryExecutionStatusResponse canaryExecutionStatusResponse = executionMapper.fromExecution(execution);
    CanaryExecutionCompletedEvent canaryExecutionCompletedEvent = new CanaryExecutionCompletedEvent(this, canaryExecutionStatusResponse);
    applicationEventPublisher.publishEvent(canaryExecutionCompletedEvent);
  }
}
