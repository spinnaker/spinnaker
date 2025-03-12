/*
 * Copyright 2023 OpsMx, Inc.
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

import okhttp3.Request;
import retrofit.RetrofitError;

/** Wraps an exception converting a successful retrofit http response body to its indicated type */
public class SpinnakerConversionException extends SpinnakerServerException {

  /**
   * Construct a SpinnakerServerException from retrofit2 with a message and cause (e.g. an exception
   * converting a response to the specified type).
   */
  public SpinnakerConversionException(String message, Throwable cause, Request request) {
    super(message, cause, request);
    setRetryable(false);
  }

  /**
   * Construct a SpinnakerConversionException from another SpinnakerConversionException (e.g. via
   * newInstance).
   */
  public SpinnakerConversionException(String message, SpinnakerConversionException cause) {
    super(message, cause);
    setRetryable(false);
  }

  /** Construct a SpinnakerConversionException corresponding to a RetrofitError. */
  public SpinnakerConversionException(RetrofitError e) {
    super(e);
    setRetryable(false);
  }

  @Override
  public SpinnakerConversionException newInstance(String message) {
    return new SpinnakerConversionException(message, this);
  }
}
