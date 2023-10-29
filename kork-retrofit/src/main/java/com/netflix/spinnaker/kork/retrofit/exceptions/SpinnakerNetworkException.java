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
import okhttp3.Request;
import retrofit.RetrofitError;

/** Represents a network error while attempting to execute a retrofit http client request. */
@NonnullByDefault
public final class SpinnakerNetworkException extends SpinnakerServerException {

  /**
   * Construct a SpinnakerNetworkException from retrofit2 with a cause (e.g. an exception sending a
   * request or processing a response).
   */
  public SpinnakerNetworkException(Throwable cause, Request request) {
    super(cause, request);
  }

  /**
   * Construct a SpinnakerNetworkException from another SpinnakerNetworkException (e.g. via
   * newInstance).
   */
  public SpinnakerNetworkException(String message, SpinnakerNetworkException cause) {
    super(message, cause);
  }

  /** Construct a SpinnakerNetworkException corresponding to a RetrofitError. */
  public SpinnakerNetworkException(RetrofitError e) {
    super(e);
  }

  @Override
  public SpinnakerNetworkException newInstance(String message) {
    return new SpinnakerNetworkException(message, this);
  }
}
