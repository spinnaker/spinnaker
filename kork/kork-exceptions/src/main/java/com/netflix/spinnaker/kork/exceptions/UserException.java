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

/** An exception thrown when the cause is a user (e.g. bad input). */
public class UserException extends SpinnakerException {
  public UserException() {}

  public UserException(String message) {
    super(message);
  }

  public UserException(String message, Throwable cause) {
    super(message, cause);
  }

  public UserException(Throwable cause) {
    super(cause);
  }

  public UserException(
      String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
    super(message, cause, enableSuppression, writableStackTrace);
  }

  public UserException(String message, String userMessage) {
    super(message, userMessage);
  }

  public UserException(String message, Throwable cause, String userMessage) {
    super(message, cause, userMessage);
  }

  public UserException(Throwable cause, String userMessage) {
    super(cause, userMessage);
  }
}
