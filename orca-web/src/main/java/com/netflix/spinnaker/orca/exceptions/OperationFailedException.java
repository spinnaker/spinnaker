package com.netflix.spinnaker.orca.exceptions;

import com.netflix.spinnaker.kork.exceptions.IntegrationException;

public class OperationFailedException extends IntegrationException {
  public OperationFailedException(String message) {
    super(message);
  }

  public OperationFailedException(String message, Throwable cause) {
    super(message, cause);
  }
}
