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
