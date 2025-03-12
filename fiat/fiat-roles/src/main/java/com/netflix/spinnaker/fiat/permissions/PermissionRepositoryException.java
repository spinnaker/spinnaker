package com.netflix.spinnaker.fiat.permissions;

import com.netflix.spinnaker.kork.exceptions.IntegrationException;

/** Base exception type for problems interacting with PermissionRepository. */
public class PermissionRepositoryException extends IntegrationException {
  public PermissionRepositoryException(String message) {
    super(message);
  }

  public PermissionRepositoryException(String message, Throwable cause) {
    super(message, cause);
  }
}
