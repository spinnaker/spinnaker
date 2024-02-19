/*
 * Copyright 2020 Google, Inc.
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

package com.netflix.spinnaker.kork.retrofit.exceptions;

import com.netflix.spinnaker.kork.annotations.NonnullByDefault;
import com.netflix.spinnaker.kork.exceptions.SpinnakerException;
import lombok.Getter;
import okhttp3.Request;
import retrofit.RetrofitError;

/** Represents an error while attempting to execute a retrofit http client request. */
@NonnullByDefault
public class SpinnakerServerException extends SpinnakerException {

  @Getter private final String url;
  @Getter private final String httpMethod;

  /** Construct a SpinnakerServerException corresponding to a RetrofitError. */
  public SpinnakerServerException(RetrofitError e) {
    super(e.getMessage(), e.getCause());
    url = e.getUrl();
    httpMethod = null;
  }

  /**
   * Construct a SpinnakerServerException from retrofit2 with no cause (e.g. a non-200 http
   * response).
   */
  public SpinnakerServerException(Request request) {
    super();
    url = request.url().toString();
    httpMethod = request.method();
  }

  /**
   * Construct a SpinnakerServerException from retrofit2 with a cause (e.g. an exception sending a
   * request or processing a response).
   */
  public SpinnakerServerException(Throwable cause, Request request) {
    super(cause);
    this.url = request.url().toString();
    this.httpMethod = request.method();
  }

  /**
   * Construct a SpinnakerServerException from retrofit2 with a message and cause (e.g. an exception
   * converting a response to the specified type).
   */
  public SpinnakerServerException(String message, Throwable cause, Request request) {
    super(message, cause);
    this.url = request.url().toString();
    this.httpMethod = request.method();
  }

  /**
   * Construct a SpinnakerServerException from another SpinnakerServerException (e.g. via
   * newInstance).
   */
  public SpinnakerServerException(String message, SpinnakerServerException cause) {
    super(message, cause);
    this.url = cause.getUrl();
    this.httpMethod = cause.getHttpMethod();
  }

  @Override
  public SpinnakerServerException newInstance(String message) {
    return new SpinnakerServerException(message, this);
  }
}
