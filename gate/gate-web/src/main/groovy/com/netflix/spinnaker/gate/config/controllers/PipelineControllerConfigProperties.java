package com.netflix.spinnaker.gate.config.controllers;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "controller.pipeline")
public class PipelineControllerConfigProperties {

  /** Holds the configurations to be used for bulk save controller mapping */
  private BulkSaveConfigProperties bulksave = new BulkSaveConfigProperties();

  @Data
  public static class BulkSaveConfigProperties {
    private int maxPollsForTaskCompletion = 300;
    private int taskCompletionCheckIntervalMs = 2000;
  }
}
