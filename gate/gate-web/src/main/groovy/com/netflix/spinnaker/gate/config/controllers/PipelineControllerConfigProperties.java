package com.netflix.spinnaker.gate.config.controllers;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "controller.pipeline")
public class PipelineControllerConfigProperties {

  /**
   * When true, a manual trigger through {@code POST /pipelines/v2/{application}/{pipelineNameOrId}}
   * is rejected with a 400 if the pipeline declares a required parameter that has no default and
   * the trigger doesn't supply it. The v2 endpoint hands the trigger to Echo asynchronously, so
   * Echo and Orca never report a missing parameter back to the caller. Enforcing it costs a front50
   * lookup of the pipeline config per trigger; turn this off to skip that lookup.
   */
  private boolean checkRequiredParametersOnExecution = true;

  /** Holds the configurations to be used for bulk save controller mapping */
  private BulkSaveConfigProperties bulksave = new BulkSaveConfigProperties();

  @Data
  public static class BulkSaveConfigProperties {
    private int maxPollsForTaskCompletion = 300;
    private int taskCompletionCheckIntervalMs = 2000;
  }
}
