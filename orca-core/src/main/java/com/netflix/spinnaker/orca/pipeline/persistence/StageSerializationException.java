package com.netflix.spinnaker.orca.pipeline.persistence;

public class StageSerializationException extends RuntimeException {
  public StageSerializationException(String message) { super(message); }

  public StageSerializationException(String message, Throwable cause) { super(message, cause); }
}
