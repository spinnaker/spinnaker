/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.gate.retrofit;

import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;

import com.netflix.spinnaker.kork.exceptions.SpinnakerException;
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerHttpException;
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerServerException;

public class UpstreamBadRequest extends SpinnakerException {

  private final int status;
  private final String url;
  private final Object error;

  private UpstreamBadRequest(SpinnakerHttpException cause) {
    super(cause.getMessage(), cause);
    this.setRetryable(cause.getRetryable());
    status = cause.getResponseCode();
    url = cause.getUrl();
    error = cause.getResponseBody();
  }

  public int getStatus() {
    return status;
  }

  public String getUrl() {
    return url;
  }

  public Object getError() {
    return error;
  }

  public static RuntimeException classifyError(SpinnakerServerException error) {
    if (error instanceof SpinnakerHttpException
        && ((SpinnakerHttpException) error).getResponseCode() < INTERNAL_SERVER_ERROR.value()) {
      return new UpstreamBadRequest((SpinnakerHttpException) error);
    } else {
      return error;
    }
  }
}
