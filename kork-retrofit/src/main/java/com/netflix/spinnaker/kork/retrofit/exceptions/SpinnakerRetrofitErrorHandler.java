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
import java.util.function.Function;
import org.springframework.http.HttpStatus;
import retrofit.ErrorHandler;
import retrofit.RetrofitError;

/**
 * An error handler to be registered with a {@link retrofit.RestAdapter}. Allows clients to catch a
 * SpinnakerServerException or something more specific (e.g. {@link SpinnakerHttpException}, or
 * {@link SpinnakerNetworkException}) depending on the properties of the {@link RetrofitError}.
 */
@NonnullByDefault
public final class SpinnakerRetrofitErrorHandler implements ErrorHandler {
  private SpinnakerRetrofitErrorHandler() {}

  /**
   * Returns an instance of a {@link SpinnakerRetrofitErrorHandler}.
   *
   * @return An instance of {@link SpinnakerRetrofitErrorHandler}
   */
  public static SpinnakerRetrofitErrorHandler getInstance() {
    return new SpinnakerRetrofitErrorHandler();
  }

  /**
   * Returns a more specific {@link Throwable} depending on properties of the caught {@link
   * RetrofitError}.
   *
   * @param e The {@link RetrofitError} thrown by an invocation of the {@link retrofit.RestAdapter}
   * @return A more informative {@link Throwable}
   */
  @Override
  public Throwable handleError(RetrofitError e) {
    switch (e.getKind()) {
      case HTTP:
        SpinnakerHttpException retval = new SpinnakerHttpException(e);
        if ((e.getResponse().getStatus() == HttpStatus.NOT_FOUND.value())
            || (e.getResponse().getStatus() == HttpStatus.BAD_REQUEST.value())) {
          retval.setRetryable(false);
        }
        return retval;
      case NETWORK:
        return new SpinnakerNetworkException(e.getMessage(), e.getCause());
      case CONVERSION:
        return new SpinnakerConversionException(e.getMessage(), e.getCause());
      default:
        return new SpinnakerServerException(e.getMessage(), e.getCause());
    }
  }

  /**
   * For SpinnakerExceptions, return a new exception of the same type with the return value of
   * handleError as its cause, with a message from messageBuilder. When handleError returns
   * something other than SpinnakerException, this method is a no-op.
   *
   * @param e The {@link RetrofitError} thrown by an invocation of the {@link retrofit.RestAdapter}
   * @param messageBuilder A function which takes in the throwable created by the handler, and
   *     outputs an error message string. The error message string is passed to a new exception
   *     which is then returned. This provides a mechanism to customize the string on the end
   *     throwable.
   * @return
   */
  public Throwable handleError(RetrofitError e, Function<Throwable, String> messageBuilder) {
    Throwable exception = handleError(e);
    if (exception instanceof SpinnakerException) {
      return ((SpinnakerException) exception).newInstance(messageBuilder.apply(exception));
    }
    return exception;
  }
}
