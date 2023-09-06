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

import com.google.common.base.Preconditions;
import com.netflix.spinnaker.kork.annotations.NullableByDefault;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import okhttp3.ResponseBody;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import retrofit.RetrofitError;
import retrofit.client.Response;
import retrofit2.Converter;
import retrofit2.Retrofit;

/**
 * An exception that exposes the {@link Response} of a given HTTP {@link RetrofitError} or {@link
 * okhttp3.Response} if retrofit 2.x used and a detail message that extracts useful information from
 * the {@link Response} or {@link okhttp3.Response}. Both {@link Response} and {@link
 * okhttp3.Response} can't be set together.
 */
@NullableByDefault
@Slf4j
public class SpinnakerHttpException extends SpinnakerServerException {

  private final Response response;

  private HttpHeaders headers;

  private final retrofit2.Response<?> retrofit2Response;

  /**
   * A message derived from a RetrofitError's response body, or null if a custom message has been
   * provided.
   */
  private final String rawMessage;

  private final Map<String, Object> responseBody;

  private final String url;

  private final int responseCode;

  /**
   * The reason from the http response. See
   * https://datatracker.ietf.org/doc/html/rfc2616#section-6.1
   */
  private final String reason;

  public SpinnakerHttpException(RetrofitError e) {
    super(e);

    // Arbitrary RetrofitErrors can have a null Response object (e.g. see
    // RetrofitError.networkError).  But, given that RetrofitError.httpError
    // assumes a non-null Response, let's do the same in SpinnakerHttpException.
    Objects.requireNonNull(e.getResponse(), "SpinnakerHttpException requires a Response object");

    this.response = e.getResponse();
    this.retrofit2Response = null;

    String tmpMessage = null;
    Map<String, Object> body = null;
    try {
      body = (Map<String, Object>) e.getBodyAs(HashMap.class);
    } catch (Exception responseBodyException) {
      // This is only an error if the mime type indicates json, but then it's
      // not spinnaker's error, it's an arguably malformed http response.  It's
      // potentially interesting to log, but...what to include in the log
      // message?  We've already (likely) read the response body once, so unless
      // we copy it ahead of time, we can't depend on being able to e.g. attempt
      // to convert it to a String and use that in a log message (or potentially
      // even in message for this exception.  That seems like a lot to do for
      // malformed json, and even for non-json responses (e.g. html).  So, don't
      // try to log anything from the response body itself.
      log.debug(
          "unable to convert response to map ({}, {})",
          e.getUrl(),
          e.getMessage(),
          responseBodyException);
    }
    responseBody = body;
    if (responseBody != null) {
      tmpMessage = (String) responseBody.get("message");
    }
    url = e.getUrl();
    responseCode = response.getStatus();
    reason = response.getReason();
    rawMessage = tmpMessage != null ? tmpMessage : reason;
  }

  /**
   * The constructor handles the HTTP retrofit2 exception, similar to retrofit logic. It is used
   * with {@link com.netflix.spinnaker.kork.retrofit.ErrorHandlingExecutorCallAdapterFactory}.
   */
  public SpinnakerHttpException(
      retrofit2.Response<?> retrofit2Response, retrofit2.Retrofit retrofit) {
    this.response = null;
    this.retrofit2Response = retrofit2Response;
    if ((retrofit2Response.code() == HttpStatus.NOT_FOUND.value())
        || (retrofit2Response.code() == HttpStatus.BAD_REQUEST.value())) {
      setRetryable(false);
    }
    responseBody = this.getErrorBodyAs(retrofit);
    url = retrofit2Response.raw().request().url().toString();
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
   * <p>Validating only one of {@link Response} or {@link okhttp3.Response} is set at a time using
   * {@link Preconditions}.checkState.
   *
   * @param message the message
   * @param cause the cause. Note that this is required (i.e. can't be null) since in the absence of
   *     a cause or a RetrofitError that provides the cause, SpinnakerHttpException is likely not
   *     the appropriate exception class to use.
   */
  public SpinnakerHttpException(String message, SpinnakerHttpException cause) {
    super(message, cause);
    // Note that getRawMessage() is null in this case.

    Preconditions.checkState(
        !(cause.response != null && cause.retrofit2Response != null),
        "Can't set both response and retrofit2Response");

    this.response = cause.response;
    this.retrofit2Response = cause.retrofit2Response;
    rawMessage = null;
    this.responseBody = cause.responseBody;
    this.url = cause.url;
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
      if (response != null) {
        response.getHeaders().forEach(header -> headers.add(header.getName(), header.getValue()));
      } else {
        retrofit2Response
            .headers()
            .names()
            .forEach(
                key -> {
                  headers.addAll(key, retrofit2Response.headers().values(key));
                });
      }
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

    return String.format("Status: %s, URL: %s, Message: %s", responseCode, url, getRawMessage());
  }

  @Override
  public SpinnakerHttpException newInstance(String message) {
    return new SpinnakerHttpException(message, this);
  }

  public Map<String, Object> getResponseBody() {
    return this.responseBody;
  }

  public String getUrl() {
    return this.url;
  }

  public String getReason() {
    return this.reason;
  }

  /**
   * HTTP response body converted to specified {@code type}. {@code null} if there is no response.
   *
   * @throws RuntimeException wrapping the underlying IOException if unable to convert the body to
   *     the specified {@code type}.
   */
  private Map<String, Object> getErrorBodyAs(Retrofit retrofit) {
    if (retrofit2Response == null) {
      return null;
    }

    Converter<ResponseBody, Map> converter =
        retrofit.responseBodyConverter(Map.class, new Annotation[0]);
    try {
      return converter.convert(retrofit2Response.errorBody());
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
