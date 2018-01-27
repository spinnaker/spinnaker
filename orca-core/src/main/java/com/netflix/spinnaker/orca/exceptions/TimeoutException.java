package com.netflix.spinnaker.orca.exceptions;

public class TimeoutException extends RuntimeException {
  public TimeoutException(String message) { super(message);}
}
