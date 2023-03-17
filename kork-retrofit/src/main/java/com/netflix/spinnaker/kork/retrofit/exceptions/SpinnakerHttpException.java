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
import org.springframework.http.HttpHeaders;
import retrofit.RetrofitError;
import retrofit.client.Response;

/**
 * An exception that exposes the {@link Response} of a given HTTP {@link RetrofitError} and a detail
 * message that extracts useful information from the {@link Response}.
 */
@NonnullByDefault
public class SpinnakerHttpException extends SpinnakerServerException {
  private final Response response;
  private HttpHeaders headers;

  public SpinnakerHttpException(RetrofitError e) {
    super(e);
    this.response = e.getResponse();
  }

  /**
   * Construct a SpinnakerHttpException with a specified message. This allows code to catch a
   * SpinnakerHttpException and throw a new one with a custom message, while still allowing
   * SpinnakerRetrofitExceptionHandlers to handle the exception and respond with the appropriate
   * http status code.
   *
   * @param message the message
   * @param cause the cause. Note that this is required (i.e. can't be null) since in the absence of
   *     a cause or a RetrofitError that provides the cause, SpinnakerHttpException is likely not
   *     the appropriate exception class to use.
   */
  public SpinnakerHttpException(String message, SpinnakerHttpException cause) {
    super(message, cause);
    // Note that getRawMessage() is null in this case.
    this.response = cause.response;
  }

  public int getResponseCode() {
    return response.getStatus();
  }

  public HttpHeaders getHeaders() {
    if (headers == null) {
      headers = new HttpHeaders();
      response.getHeaders().forEach(header -> headers.add(header.getName(), header.getValue()));
    }
    return headers;
  }

  @Override
  public String getMessage() {
    // If there's no message derived from a response, get the specified message.
    // It feels a little backwards to do it this way, but super.getMessage()
    // always returns something whether there's a specified message or not, so
    // look at getRawMessage instead.
    if (getRawMessage() == null) {
      return super.getMessage();
    }
    return String.format(
        "Status: %s, URL: %s, Message: %s",
        response.getStatus(), response.getUrl(), getRawMessage());
  }
}
