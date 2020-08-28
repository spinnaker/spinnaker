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
import static retrofit.RetrofitError.Kind.HTTP;

import com.netflix.spinnaker.kork.exceptions.SpinnakerException;
import java.util.Collection;
import retrofit.RetrofitError;

public class UpstreamBadRequest extends SpinnakerException {

  private final int status;
  private final String url;
  private final Object error;

  private UpstreamBadRequest(RetrofitError cause) {
    super(cause.getMessage(), cause);
    status = cause.getResponse().getStatus();
    url = cause.getUrl();
    error = cause.getBody();
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

  public static Exception classifyError(RetrofitError error) {
    if (error.getKind() == HTTP
        && error.getResponse().getStatus() < INTERNAL_SERVER_ERROR.value()) {
      return new UpstreamBadRequest(error);
    } else {
      return error;
    }
  }

  public static Exception classifyError(
      RetrofitError error, Collection<Integer> supportedHttpStatuses) {
    if (error.getKind() == HTTP
        && supportedHttpStatuses.contains(error.getResponse().getStatus())) {
      return new UpstreamBadRequest(error);
    } else {
      return error;
    }
  }
}
