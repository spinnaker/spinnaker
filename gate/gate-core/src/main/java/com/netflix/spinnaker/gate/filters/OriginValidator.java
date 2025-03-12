package com.netflix.spinnaker.gate.filters;

public interface OriginValidator {
  boolean isValidOrigin(String origin);

  default boolean isExpectedOrigin(String origin) {
    return isValidOrigin(origin);
  }
}
