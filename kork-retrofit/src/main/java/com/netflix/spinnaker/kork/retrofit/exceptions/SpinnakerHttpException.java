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
import lombok.Getter;
import retrofit.RetrofitError;
import retrofit.client.Response;

/**
 * An exception that exposes the {@link Response} of a given HTTP {@link RetrofitError} and a detail
 * message that extracts useful information from the {@link Response}.
 */
@Getter
@NonnullByDefault
public final class SpinnakerHttpException extends SpinnakerServerException {
  private final Response response;

  public SpinnakerHttpException(RetrofitError e) {
    super(e);
    this.response = e.getResponse();
  }

  @Override
  public String getMessage() {
    return String.format(
        "Status: %s, URL: %s, Message: %s",
        response.getStatus(), response.getUrl(), getRawMessage());
  }
}
