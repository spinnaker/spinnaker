package com.netflix.kayenta.events;

import com.netflix.kayenta.canary.CanaryExecutionStatusResponse;
import org.springframework.context.ApplicationEvent;

public class CanaryExecutionCompletedEvent extends ApplicationEvent {
  private final CanaryExecutionStatusResponse canaryExecutionStatusResponse;

  public CanaryExecutionCompletedEvent(Object source, CanaryExecutionStatusResponse canaryExecutionStatusResponse) {
    super(source);
    this.canaryExecutionStatusResponse = canaryExecutionStatusResponse;
  }

  public CanaryExecutionStatusResponse getCanaryExecutionStatusResponse() {
    return canaryExecutionStatusResponse;
  }
}
