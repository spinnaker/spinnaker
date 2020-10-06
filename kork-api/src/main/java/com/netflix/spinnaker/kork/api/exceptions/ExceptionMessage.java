package com.netflix.spinnaker.kork.api.exceptions;

import com.netflix.spinnaker.kork.plugins.api.internal.SpinnakerExtensionPoint;
import java.util.Optional;
import javax.annotation.Nullable;

/**
 * An extension point to create exception messages, typically for end-users. Note that the original
 * message on the exception can not be modified as it is immutable - the message generated here will
 * always be appended to the original message.
 *
 * <p>This can be used to provide more failure-scenario context to end-users.
 */
public interface ExceptionMessage extends SpinnakerExtensionPoint {

  /**
   * Create the message. If markdown is included, it will be rendered correctly in the UI.
   *
   * @param throwable The thrown exception. Used to help provide context when creating the message.
   * @param exceptionDetails Additional details about the exception that are possibly not present on
   *     the exception itself which can help provide context when creating the message.
   * @return The string to append to the message. Note that this will not modify the original
   *     exception message but only append to the message that is delivered to the end-user.
   */
  Optional<String> message(Throwable throwable, @Nullable ExceptionDetails exceptionDetails);

  /**
   * Create the message. If markdown is included, it will be rendered correctly in the UI.
   *
   * @param errorCode The error code. This typically comes into play when using Spring's Errors
   *     during validation, prior to throwing an exception.
   * @param exceptionDetails Additional details about the error that can be used to inform the
   *     message.
   * @return The string to append to the message. Note that this will not modify the original
   *     exception message but only append to the message that is delivered to the end-user.
   */
  Optional<String> message(String errorCode, @Nullable ExceptionDetails exceptionDetails);
}
