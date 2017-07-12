package com.netflix.spinnaker.orca.pipeline.persistence;

import com.netflix.spinnaker.kork.web.exceptions.NotFoundException;

public class ExecutionNotFoundException extends NotFoundException {
  public ExecutionNotFoundException(String msg) {
    super(msg);
  }
}
