package com.netflix.spinnaker.front50.exception;

import com.netflix.spinnaker.kork.exceptions.UserException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = HttpStatus.BAD_REQUEST)
public class BadRequestException extends UserException {
  public BadRequestException() {}

  public BadRequestException(String message) {}

  public BadRequestException(String message, Throwable cause) {}

  public BadRequestException(Throwable cause) {}

  protected BadRequestException(
      String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {}
}
