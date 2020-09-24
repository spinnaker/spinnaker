package com.netflix.spinnaker.kork.api.exceptions;

import com.netflix.spinnaker.kork.plugins.api.internal.SpinnakerExtensionPoint;
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
   * The user message generated will largely be based off the exception type, so check if this
   * implementation supports the specified exception type.
   */
  boolean supports(Class<? extends Throwable> throwable);

  /**
   * Create the message.
   *
   * @param throwable The thrown exception. Used to help provide context when creating the message.
   * @param exceptionDetails Additional details about the exception that are possibly not present on
   *     the exception itself which can help provide context when creating the message.
   * @return The string to append to the message. Note that this will not modify the original
   *     exception message but only append to the message that is delivered to the end-user.
   */
  @Nullable
  String message(Throwable throwable, @Nullable ExceptionDetails exceptionDetails);
}
