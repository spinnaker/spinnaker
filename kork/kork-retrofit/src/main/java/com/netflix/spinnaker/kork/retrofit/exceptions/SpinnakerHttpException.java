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

import com.netflix.spinnaker.kork.annotations.NullableByDefault;
import java.lang.annotation.Annotation;
import java.util.Map;
import javax.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import okhttp3.ResponseBody;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import retrofit2.Converter;
import retrofit2.Retrofit;

/**
 * An exception that exposes the {@link okhttp3.Response} of a given HTTP exception and a detail
 * message that extracts useful information from the {@link okhttp3.Response}
 */
@NullableByDefault
@Slf4j
public class SpinnakerHttpException extends SpinnakerServerException {

  private HttpHeaders headers;

  private final retrofit2.Response<?> retrofit2Response;

  /** A message derived from the response body, or null if a custom message has been provided. */
  private final String rawMessage;

  private final Map<String, Object> responseBody;

  private final int responseCode;

  /**
   * The reason from the http response. See
   * https://datatracker.ietf.org/doc/html/rfc2616#section-6.1
   */
  private final String reason;

  /**
   * The constructor handles the HTTP retrofit2 exception, similar to retrofit logic. It is used
   * with {@link com.netflix.spinnaker.kork.retrofit.ErrorHandlingExecutorCallAdapterFactory}.
   */
  public SpinnakerHttpException(
      retrofit2.Response<?> retrofit2Response, retrofit2.Retrofit retrofit) {
    super(retrofit2Response.raw().request());
    this.retrofit2Response = retrofit2Response;
    if ((retrofit2Response.code() == HttpStatus.NOT_FOUND.value())
        || (retrofit2Response.code() == HttpStatus.BAD_REQUEST.value())) {
      setRetryable(false);
    }
    responseBody = this.getErrorBodyAs(retrofit);
    responseCode = retrofit2Response.code();
    reason = retrofit2Response.message();
    this.rawMessage =
        responseBody != null
            ? (String) responseBody.getOrDefault("message", retrofit2Response.message())
            : retrofit2Response.message();
  }

  private String getRawMessage() {
    return rawMessage;
  }

  /**
   * Construct a SpinnakerHttpException with a specified message. This allows code to catch a
   * SpinnakerHttpException and throw a new one with a custom message, while still allowing
   * SpinnakerRetrofitExceptionHandlers to handle the exception and respond with the appropriate
   * http status code.
   *
   * @param message the message
   * @param cause the cause. Note that this is required (i.e. can't be null) since in the absence of
   *     a cause, SpinnakerHttpException is likely not the appropriate exception class to use.
   */
  public SpinnakerHttpException(String message, SpinnakerHttpException cause) {
    super(message, cause);
    // Note that getRawMessage() is null in this case.

    this.retrofit2Response = cause.retrofit2Response;
    rawMessage = null;
    this.responseBody = cause.responseBody;
    this.responseCode = cause.responseCode;
    this.reason = cause.reason;
  }

  public int getResponseCode() {
    return responseCode;
  }

  @Nonnull
  public HttpHeaders getHeaders() {
    if (headers == null) {
      headers = new HttpHeaders();
      retrofit2Response
          .headers()
          .names()
          .forEach(
              key -> {
                headers.addAll(key, retrofit2Response.headers().values(key));
              });
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

    if (getHttpMethod() == null) {
      return String.format(
          "Status: %s, URL: %s, Message: %s", responseCode, this.getUrl(), getRawMessage());
    }

    return String.format(
        "Status: %s, Method: %s, URL: %s, Message: %s",
        responseCode, getHttpMethod(), this.getUrl(), getRawMessage());
  }

  @Override
  public SpinnakerHttpException newInstance(String message) {
    return new SpinnakerHttpException(message, this);
  }

  public Map<String, Object> getResponseBody() {
    return this.responseBody;
  }

  public String getReason() {
    return this.reason;
  }

  /**
   * HTTP error response body converted to the specified {@code type}.
   *
   * @return null if there's no response or unable to convert the body to the specified {@code
   *     type}.
   */
  private Map<String, Object> getErrorBodyAs(Retrofit retrofit) {
    if (retrofit2Response == null) {
      return null;
    }

    Converter<ResponseBody, Map> converter =
        retrofit.responseBodyConverter(Map.class, new Annotation[0]);
    try {
      return converter.convert(retrofit2Response.errorBody());
    } catch (Exception e) {
      log.debug(
          "unable to convert response to map ({} {}, {})",
          retrofit2Response.raw().request().method(),
          retrofit2Response.code(),
          retrofit2Response.raw().request().url(),
          e);
      return null;
    }
  }
}
