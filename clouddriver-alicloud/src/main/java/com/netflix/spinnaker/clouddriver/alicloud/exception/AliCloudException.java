package com.netflix.spinnaker.clouddriver.alicloud.exception;

import com.netflix.spinnaker.kork.exceptions.IntegrationException;

public class AliCloudException extends IntegrationException {

  public AliCloudException(String message) {
    super(message);
  }

  public AliCloudException(Exception e) {
    super(e);
  }
}
