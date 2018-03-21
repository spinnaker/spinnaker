package com.netflix.spinnaker.orca.pipeline.persistence;

public class UnsupportedRepositoryException extends RuntimeException {
  public UnsupportedRepositoryException(String message) { super(message); }

  public UnsupportedRepositoryException(String message, Throwable cause) { super(message, cause); }
}
