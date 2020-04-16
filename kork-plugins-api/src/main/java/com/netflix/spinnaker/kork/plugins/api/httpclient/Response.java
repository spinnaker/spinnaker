/*
 * Copyright 2020 Netflix, Inc.
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
package com.netflix.spinnaker.kork.plugins.api.httpclient;

import com.netflix.spinnaker.kork.annotations.Beta;
import java.io.InputStream;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nonnull;

/** HTTP Response wrapper. */
@Beta
public interface Response {

  /** Get the response body. */
  InputStream getBody();

  /** Get the response body, casting it into the expected type. */
  <T> T getBody(@Nonnull Class<T> expectedType);

  /** Get the error exception, if present. */
  @Nonnull
  Optional<Exception> getException();

  /** Get the response code. */
  int getStatusCode();

  /** Get the response headers. */
  @Nonnull
  Map<String, String> getHeaders();

  /**
   * Returns whether or not the request had an error (4xx/5xx response code or underlying
   * IOException)
   */
  default boolean isError() {
    return getException().isPresent() || getStatusCode() >= 400;
  }
}
