package com.netflix.spinnaker.kork.web.exceptions;

import com.netflix.spinnaker.kork.api.exceptions.ExceptionDetails;
import com.netflix.spinnaker.kork.api.exceptions.ExceptionMessage;
import java.util.List;
import javax.annotation.Nullable;
import org.springframework.beans.factory.ObjectProvider;

/**
 * Used to add additional information to an exception message. Messages on exceptions are immutable,
 * so this is mostly used when a message is pulled from an exception prior to being sent to an
 * end-user.
 */
public class ExceptionMessageDecorator {

  private final ObjectProvider<List<ExceptionMessage>> exceptionMessagesProvider;
  private final String NEWLINE = "\n\n";

  public ExceptionMessageDecorator(
      ObjectProvider<List<ExceptionMessage>> exceptionMessagesProvider) {
    this.exceptionMessagesProvider = exceptionMessagesProvider;
  }

  /**
   * Decorate an exception message give the provided arguments.
   *
   * @param throwable {@link Throwable}
   * @param message The exception message (which can be different from the message on the thrown
   *     exception).
   * @param exceptionDetails Additional {@link ExceptionDetails} about the exception.
   * @return The final exception message for the end-user.
   */
  public String decorate(
      Throwable throwable, String message, @Nullable ExceptionDetails exceptionDetails) {
    return decorate(throwable, null, message, exceptionDetails);
  }

  /**
   * Decorate an exception message give the provided arguments.
   *
   * @param errorCode The error code, typically from a validation error
   * @param message The message related to the error code
   * @param exceptionDetails Additional {@link ExceptionDetails} about the exception.
   * @return The final exception message for the end-user.
   */
  public String decorate(
      String errorCode, String message, @Nullable ExceptionDetails exceptionDetails) {
    return decorate(null, errorCode, message, exceptionDetails);
  }

  public String decorate(Throwable throwable, String message) {
    return decorate(throwable, message, null);
  }

  public String decorate(String errorCode, String message) {
    return decorate(errorCode, message, null);
  }

  private String decorate(
      @Nullable Throwable throwable,
      @Nullable String errorCode,
      String message,
      @Nullable ExceptionDetails exceptionDetails) {

    StringBuilder sb = new StringBuilder().append(message);

    List<ExceptionMessage> exceptionMessages = exceptionMessagesProvider.getIfAvailable();
    if (exceptionMessages != null && !exceptionMessages.isEmpty()) {
      for (ExceptionMessage exceptionMessage : exceptionMessages) {
        if (throwable != null) {
          exceptionMessage
              .message(throwable, exceptionDetails)
              .ifPresent(s -> sb.append(NEWLINE).append(s));
        }
        if (errorCode != null) {
          exceptionMessage
              .message(errorCode, exceptionDetails)
              .ifPresent(s -> sb.append(NEWLINE).append(s));
        }
      }
    }

    return sb.toString();
  }
}
