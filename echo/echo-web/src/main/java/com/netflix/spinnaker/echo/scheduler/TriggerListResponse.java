package com.netflix.spinnaker.echo.scheduler;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class TriggerListResponse {
  private final List<TriggerDescription> pipeline;

  private final List<TriggerDescription> manuallyCreated;
}
