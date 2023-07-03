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
import retrofit.RetrofitError;

/** Wraps an exception of kind {@link RetrofitError.Kind} NETWORK. */
@NonnullByDefault
public final class SpinnakerNetworkException extends SpinnakerServerException {
  public SpinnakerNetworkException(Throwable cause) {
    super(cause);
  }

  public SpinnakerNetworkException(String message, Throwable cause) {
    super(message, cause);
  }

  @Override
  public SpinnakerNetworkException newInstance(String message) {
    return new SpinnakerNetworkException(message, this);
  }
}
