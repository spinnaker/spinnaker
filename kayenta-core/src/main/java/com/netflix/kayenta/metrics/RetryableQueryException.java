package com.netflix.kayenta.metrics;

public class RetryableQueryException extends RuntimeException {
  public RetryableQueryException(String message) {
    super(message);
  }
}
