/*
 * Copyright 2019 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.google.compute;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.api.services.compute.ComputeRequest;
import java.io.IOException;
import javax.annotation.Nullable;

public class FakeGoogleComputeRequest<RequestT extends ComputeRequest<ResponseT>, ResponseT>
    implements GoogleComputeGetRequest<RequestT, ResponseT> {

  private final RequestT request;
  private final ResponseT response;
  private final IOException exception;

  private boolean executed = false;

  public static <RequestT extends ComputeRequest<ResponseT>, ResponseT>
      FakeGoogleComputeRequest<RequestT, ResponseT> createWithResponse(ResponseT response) {
    return createWithResponse(response, /* request= */ null);
  }

  public static <RequestT extends ComputeRequest<ResponseT>, ResponseT>
      FakeGoogleComputeRequest<RequestT, ResponseT> createWithResponse(
          ResponseT response, RequestT request) {
    return new FakeGoogleComputeRequest<>(response, request);
  }

  public static <RequestT extends ComputeRequest<ResponseT>, ResponseT>
      FakeGoogleComputeRequest<RequestT, ResponseT> createWithException(IOException exception) {
    return createWithException(exception, /* request= */ null);
  }

  public static <RequestT extends ComputeRequest<ResponseT>, ResponseT>
      FakeGoogleComputeRequest<RequestT, ResponseT> createWithException(
          IOException exception, RequestT request) {
    return new FakeGoogleComputeRequest<>(exception, request);
  }

  FakeGoogleComputeRequest(ResponseT response, @Nullable RequestT request) {
    checkNotNull(response);
    this.request = request;
    this.response = response;
    this.exception = null;
  }

  FakeGoogleComputeRequest(IOException exception, @Nullable RequestT request) {
    checkNotNull(exception);
    this.request = request;
    this.response = null;
    this.exception = exception;
  }

  @Override
  public ResponseT execute() throws IOException {
    executed = true;
    if (exception != null) {
      throw exception;
    }
    return response;
  }

  @Override
  public RequestT getRequest() {
    if (request == null) {
      throw new UnsupportedOperationException("FakeGoogleComputeRequest#getRequest()");
    }
    return request;
  }

  public boolean executed() {
    return executed;
  }
}
