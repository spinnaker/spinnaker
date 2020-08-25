package com.netflix.spinnaker.clouddriver.exceptions;

import com.netflix.spinnaker.kork.exceptions.IntegrationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = HttpStatus.BAD_REQUEST)
public class CloudProviderNotFoundException extends IntegrationException {
  public CloudProviderNotFoundException(String message) {
    super(message);
  }

  public CloudProviderNotFoundException(String message, Throwable cause) {
    super(message, cause);
  }

  public CloudProviderNotFoundException(Throwable cause) {
    super(cause);
  }

  public CloudProviderNotFoundException(String message, String userMessage) {
    super(message, userMessage);
  }

  public CloudProviderNotFoundException(String message, Throwable cause, String userMessage) {
    super(message, cause, userMessage);
  }

  public CloudProviderNotFoundException(Throwable cause, String userMessage) {
    super(cause, userMessage);
  }
}
