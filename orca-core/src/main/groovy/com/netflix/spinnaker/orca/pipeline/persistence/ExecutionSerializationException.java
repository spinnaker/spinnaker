package com.netflix.spinnaker.orca.pipeline.persistence;

public class ExecutionSerializationException extends RuntimeException {
  public ExecutionSerializationException(String message) { super(message); }

  public ExecutionSerializationException(String message, Throwable cause) { super(message, cause); }
}
