/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.kork.web.exceptions;

import com.netflix.spinnaker.kork.exceptions.HasAdditionalAttributes;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

public class ValidationException extends InvalidRequestException
    implements HasAdditionalAttributes {
  private final Collection errors;

  public ValidationException(Collection errors) {
    this.errors = errors;
  }

  public ValidationException(String message, Collection errors) {
    super(message);
    this.errors = errors;
  }

  public ValidationException(String message, Throwable cause, Collection errors) {
    super(message, cause);
    this.errors = errors;
  }

  public ValidationException(Throwable cause, Collection errors) {
    super(cause);
    this.errors = errors;
  }

  public ValidationException(
      String message,
      Throwable cause,
      boolean enableSuppression,
      boolean writableStackTrace,
      Collection errors) {
    super(message, cause, enableSuppression, writableStackTrace);
    this.errors = errors;
  }

  @Override
  public Map<String, Object> getAdditionalAttributes() {
    return errors != null && !errors.isEmpty()
        ? Collections.singletonMap("errors", errors)
        : Collections.emptyMap();
  }
}
