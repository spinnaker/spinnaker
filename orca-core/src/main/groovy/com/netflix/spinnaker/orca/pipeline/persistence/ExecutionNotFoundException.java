package com.netflix.spinnaker.orca.pipeline.persistence;

public class ExecutionNotFoundException extends RuntimeException {
  public ExecutionNotFoundException(String msg) {
    super(msg);
  }
}
