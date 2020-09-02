package com.netflix.spinnaker.fiat.permissions;

/**
 * An exception reading permission values.
 *
 * <p>Reading permissions is side-effect free so this exception is retryable by default.
 */
public class PermissionReadException extends PermissionRepositoryException {
  public PermissionReadException(String message) {
    super(message);
    setRetryable(true);
  }

  public PermissionReadException(String message, Throwable cause) {
    super(message, cause);
    setRetryable(true);
  }
}
