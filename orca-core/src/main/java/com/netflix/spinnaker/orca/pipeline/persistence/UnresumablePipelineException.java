package com.netflix.spinnaker.orca.pipeline.persistence;

public class UnresumablePipelineException extends IllegalStateException {
  public UnresumablePipelineException(String message) { super(message); }

  public UnresumablePipelineException(String message, Throwable cause) { super(message, cause); }
}
