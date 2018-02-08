package com.netflix.kayenta.metrics;

public class FatalQueryException extends RuntimeException {
  public FatalQueryException(String message) {
    super(message);
  }
}
