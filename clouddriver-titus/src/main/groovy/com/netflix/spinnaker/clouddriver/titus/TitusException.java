/*
 * Copyright 2018 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.titus;

import com.netflix.spinnaker.kork.exceptions.IntegrationException;

public class TitusException extends IntegrationException {
  public TitusException(String message) {
    super(message);
    setRetryable(true);
  }

  public TitusException(String message, Throwable cause) {
    super(message, cause);
    setRetryable(isExceptionRetryable(cause));
  }

  public TitusException(Throwable cause) {
    super(cause);
    setRetryable(isExceptionRetryable(cause));
  }

  public TitusException(String message, String userMessage) {
    super(message, userMessage);
    setRetryable(true);
  }

  public TitusException(String message, Throwable cause, String userMessage) {
    super(message, cause, userMessage);
    setRetryable(isExceptionRetryable(cause));
  }

  private static boolean isExceptionRetryable(Throwable cause) {
    final String message = cause.getMessage();
    if (message == null) {
      return true;
    }

    // If the request sent to Titus is invalid, there's no sense is retrying.
    return !message.startsWith("INVALID_ARGUMENT");
  }
}
