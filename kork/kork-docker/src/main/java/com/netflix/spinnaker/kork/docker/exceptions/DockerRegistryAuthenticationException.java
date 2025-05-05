package com.netflix.spinnaker.kork.docker.exceptions;

import com.netflix.spinnaker.kork.exceptions.CredentialsException;

public class DockerRegistryAuthenticationException extends CredentialsException {
  public DockerRegistryAuthenticationException() {}

  public DockerRegistryAuthenticationException(String message) {
    super(message);
  }

  public DockerRegistryAuthenticationException(String message, Throwable cause) {
    super(message, cause);
  }

  public DockerRegistryAuthenticationException(Throwable cause) {
    super(cause);
  }
}
