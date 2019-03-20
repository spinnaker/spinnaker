package com.netflix.spinnaker.cats.cache;

public class UnsupportedCacheMethodException extends RuntimeException {
  public UnsupportedCacheMethodException(String message) {
    super(message);
  }
}
