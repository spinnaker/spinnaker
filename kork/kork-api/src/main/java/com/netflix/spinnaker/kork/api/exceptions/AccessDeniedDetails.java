package com.netflix.spinnaker.kork.api.exceptions;

import javax.annotation.Nullable;

/**
 * Details regarding an access denied exception.
 *
 * <p>For now, this object just uses strings to represent resource type and authorization, but
 * eventually should support the {@code Authorization} and {@code ResourceType} types provided by
 * {@code kork-authz}.
 */
public class AccessDeniedDetails implements ExceptionDetails {
  private final String resourceType;
  private final String resourceName;
  @Nullable private final String authorization;

  public AccessDeniedDetails(
      String resourceType, String resourceName, @Nullable String authorization) {
    this.resourceType = resourceType;
    this.resourceName = resourceName;
    this.authorization = authorization;
  }

  public String getResourceType() {
    return resourceType;
  }

  public String getResourceName() {
    return resourceName;
  }

  @Nullable
  public String getAuthorization() {
    return authorization;
  }

  public boolean hasAuthorization() {
    return authorization != null;
  }
}
