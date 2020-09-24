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

  public ExceptionMessageDecorator(
      ObjectProvider<List<ExceptionMessage>> exceptionMessagesProvider) {
    this.exceptionMessagesProvider = exceptionMessagesProvider;
  }

  /**
   * Provided an exception type, original message, and optional exception details, return a message
   * for the end-user.
   *
   * @param throwable {@link Throwable}
   * @param message The exception message (which can be different from the message on the thrown
   *     exception).
   * @param exceptionDetails Additional {@link ExceptionDetails} about the exception.
   * @return The final exception message for the end-user.
   */
  public String decorate(
      Throwable throwable, String message, @Nullable ExceptionDetails exceptionDetails) {
    List<ExceptionMessage> exceptionMessages = exceptionMessagesProvider.getIfAvailable();

    StringBuilder sb = new StringBuilder();
    sb.append(message);

    if (exceptionMessages != null && !exceptionMessages.isEmpty()) {
      for (ExceptionMessage exceptionMessage : exceptionMessages) {
        if (exceptionMessage.supports(throwable.getClass())) {
          String messageToAppend = exceptionMessage.message(throwable, exceptionDetails);
          if (messageToAppend != null && !messageToAppend.isEmpty()) {
            sb.append("\n").append(messageToAppend);
          }
        }
      }
    }

    return sb.toString();
  }

  public String decorate(Throwable throwable, String message) {
    return decorate(throwable, message, null);
  }
}
