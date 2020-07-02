package com.netflix.spinnaker.cats.cache;

import com.netflix.spinnaker.kork.annotations.Beta;

@Beta
public class UnsupportedCacheMethodException extends RuntimeException {
  public UnsupportedCacheMethodException(String message) {
    super(message);
  }
}
