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

/** An exception thrown by non-core Spinnaker code (e.g. cloud providers, vendor stages, etc.) */
public class IntegrationException extends SpinnakerException {
  public IntegrationException(String message) {
    super(message);
  }

  public IntegrationException(String message, Throwable cause) {
    super(message, cause);
  }

  public IntegrationException(Throwable cause) {
    super(cause);
  }

  public IntegrationException(String message, String userMessage) {
    super(message, userMessage);
  }

  public IntegrationException(String message, Throwable cause, String userMessage) {
    super(message, cause, userMessage);
  }

  public IntegrationException(Throwable cause, String userMessage) {
    super(cause, userMessage);
  }
}
