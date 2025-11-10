package com.netflix.spinnaker.echo.scheduler;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TriggerDescription {
  @NotBlank private String id;

  @NotBlank private String application;

  @NotBlank private String pipelineId;

  @NotBlank private String cronExpression;

  private String timezone;

  private String runAsUser;

  private Boolean forceRebake;
}
