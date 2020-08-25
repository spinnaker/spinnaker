package com.netflix.spinnaker.clouddriver.exceptions;

import com.netflix.spinnaker.kork.exceptions.SystemException;

public class OperationTimedOutException extends SystemException {
  public OperationTimedOutException(String message) {
    super(message);
  }

  public OperationTimedOutException(String message, Throwable cause) {
    super(message, cause);
  }

  public OperationTimedOutException(Throwable cause) {
    super(cause);
  }

  public OperationTimedOutException(String message, String userMessage) {
    super(message, userMessage);
  }

  public OperationTimedOutException(String message, Throwable cause, String userMessage) {
    super(message, cause, userMessage);
  }

  public OperationTimedOutException(Throwable cause, String userMessage) {
    super(cause, userMessage);
  }
}
