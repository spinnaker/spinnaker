/*
 * Copyright 2025 Apple Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
package com.netflix.spinnaker.kork.artifacts.artifactstore.exceptions;

import com.netflix.spinnaker.kork.exceptions.SpinnakerException;
import org.springframework.http.HttpStatus;

/** Exception indicating a bad request to the artifact store. */
public class ArtifactStoreBadRequestException extends SpinnakerException {

  public ArtifactStoreBadRequestException(String message) {
    super(message);
  }

  public ArtifactStoreBadRequestException(String message, Throwable cause) {
    super(message, cause);
  }

  @Override
  public HttpStatus getStatus() {
    return HttpStatus.BAD_REQUEST;
  }
}
