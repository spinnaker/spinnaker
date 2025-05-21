/*
 * Copyright 2025 Harness, Inc.
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

package com.netflix.spinnaker.kork.docker.exceptions;

import com.netflix.spinnaker.kork.exceptions.SpinnakerException;

public class DockerRegistryOperationException extends SpinnakerException {
  public DockerRegistryOperationException() {}

  public DockerRegistryOperationException(String message) {
    super(message);
  }

  public DockerRegistryOperationException(String message, Throwable cause) {
    super(message, cause);
  }

  public DockerRegistryOperationException(Throwable cause) {
    super(cause);
  }

  public DockerRegistryOperationException(String message, String userMessage) {
    super(message, userMessage);
  }

  public DockerRegistryOperationException(String message, Throwable cause, String userMessage) {
    super(message, cause, userMessage);
  }

  public DockerRegistryOperationException(Throwable cause, String userMessage) {
    super(cause, userMessage);
  }

  public DockerRegistryOperationException(
      String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
    super(message, cause, enableSuppression, writableStackTrace);
  }
}
