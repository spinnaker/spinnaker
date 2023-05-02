/*
 * Copyright 2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.kork.exceptions;

import javax.annotation.Nullable;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/** A root-level marker interface for all exceptions to be thrown by Spinnaker code. */
@Getter
public class SpinnakerException extends RuntimeException implements HasAdditionalAttributes {

  /**
   * An optional, end-user friendly message.
   *
   * <p>In most cases, an exception message is more suitable for developers and operators, but can
   * be confusing for an end-user. This field provides a standard method of propagating messaging up
   * to the edge.
   */
  @Nullable private String userMessage;

  /**
   * Whether or not the exception is explicitly known to be retryable.
   *
   * <p>If the result is NULL, the exception's retry characteristics are undefined and thus retries
   * on the original logic that caused the exception may have undefined behavior.
   */
  @Setter
  @Nullable
  @Accessors(chain = true)
  private Boolean retryable;

  public SpinnakerException() {}

  public SpinnakerException(String message) {
    super(message);
  }

  public SpinnakerException(String message, Throwable cause) {
    super(message, cause);
  }

  public SpinnakerException(Throwable cause) {
    super(cause);
  }

  public SpinnakerException(String message, String userMessage) {
    super(message);
    this.userMessage = userMessage;
  }

  public SpinnakerException(String message, Throwable cause, String userMessage) {
    super(message, cause);
    this.userMessage = userMessage;
  }

  public SpinnakerException(Throwable cause, String userMessage) {
    super(cause);
    this.userMessage = userMessage;
  }

  public SpinnakerException(
      String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
    super(message, cause, enableSuppression, writableStackTrace);
  }

  /**
   * Creates a new instance of the exception, but with a new message.
   *
   * <p>This method in and of itself is not really useful (using something like new
   * SpinnakerException(message, e) would work). However, it becomes useful when all other child
   * classes of SpinnakerException override it.
   *
   * <p>This allows a caller to use a generic catch statement to update the message, all in one
   * line, i.e
   *
   * <pre>
   *   catch (SpinnakerException e) {
   *    // if the child class has a newInstance override, the return type will stay
   *    // the type of the child class
   *    return e.newInstance("new message");
   *   }
   * </pre>
   *
   * @param message
   * @return
   */
  public SpinnakerException newInstance(String message) {
    return new SpinnakerException(message, this);
  }
}
