package com.netflix.spinnaker.kork.docker.exceptions;

import com.netflix.spinnaker.kork.exceptions.SpinnakerException;

public class DockerRegistryOperationException extends SpinnakerException {
  public DockerRegistryOperationException() {}

  public DockerRegistryOperationException(String message) {
    super(message);
  }

  public DockerRegistryOperationException(String message, Throwable cause) {
    super(message, cause);
  }

  public DockerRegistryOperationException(Throwable cause) {
    super(cause);
  }

  public DockerRegistryOperationException(String message, String userMessage) {
    super(message, userMessage);
  }

  public DockerRegistryOperationException(String message, Throwable cause, String userMessage) {
    super(message, cause, userMessage);
  }

  public DockerRegistryOperationException(Throwable cause, String userMessage) {
    super(cause, userMessage);
  }

  public DockerRegistryOperationException(
      String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
    super(message, cause, enableSuppression, writableStackTrace);
  }
}
