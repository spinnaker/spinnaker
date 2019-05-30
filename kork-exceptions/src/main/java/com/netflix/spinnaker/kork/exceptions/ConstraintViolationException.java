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

/**
 * Thrown when an "expected" possible constraint is violated. This isn't necessarily the fault of
 * the system, an integration, or a user. A prime example would be a preconditions check, traffic
 * guards, and the like.
 */
public class ConstraintViolationException extends SpinnakerException {
  public ConstraintViolationException(String message) {
    super(message);
  }

  public ConstraintViolationException(String message, Throwable cause) {
    super(message, cause);
  }

  public ConstraintViolationException(Throwable cause) {
    super(cause);
  }

  public ConstraintViolationException(String message, String userMessage) {
    super(message, userMessage);
  }

  public ConstraintViolationException(String message, Throwable cause, String userMessage) {
    super(message, cause, userMessage);
  }

  public ConstraintViolationException(Throwable cause, String userMessage) {
    super(cause, userMessage);
  }
}
