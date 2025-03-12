package com.netflix.spinnaker.clouddriver.deploy;

import com.netflix.spinnaker.kork.exceptions.IntegrationException;

public class DeployHandlerNotFoundException extends IntegrationException {
  public DeployHandlerNotFoundException(String message) {
    super(message);
  }

  public DeployHandlerNotFoundException(String message, Throwable cause) {
    super(message, cause);
  }

  public DeployHandlerNotFoundException(Throwable cause) {
    super(cause);
  }

  public DeployHandlerNotFoundException(String message, String userMessage) {
    super(message, userMessage);
  }

  public DeployHandlerNotFoundException(String message, Throwable cause, String userMessage) {
    super(message, cause, userMessage);
  }

  public DeployHandlerNotFoundException(Throwable cause, String userMessage) {
    super(cause, userMessage);
  }
}
