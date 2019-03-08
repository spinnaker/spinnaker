package com.netflix.spinnaker.echo.scheduler;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class TriggerListResponse {
  private final List<TriggerDescription> pipeline;

  private final List<TriggerDescription> manuallyCreated;
}
