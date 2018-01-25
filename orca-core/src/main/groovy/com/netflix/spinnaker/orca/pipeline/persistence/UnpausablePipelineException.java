package com.netflix.spinnaker.orca.pipeline.persistence;

public class UnpausablePipelineException extends IllegalStateException {
  public UnpausablePipelineException(String message) { super(message); }

  public UnpausablePipelineException(String message, Throwable cause) { super(message, cause); }
}
