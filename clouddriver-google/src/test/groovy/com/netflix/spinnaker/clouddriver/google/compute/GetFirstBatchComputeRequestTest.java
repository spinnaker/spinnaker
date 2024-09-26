/*
 * Copyright 2019 Google, LLC
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

package com.netflix.spinnaker.clouddriver.google.compute;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.api.client.googleapis.testing.json.GoogleJsonResponseExceptionFactoryTesting;
import com.google.api.client.http.HttpResponseException;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.compute.Compute;
import com.google.api.services.compute.model.Image;
import java.io.IOException;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class GetFirstBatchComputeRequestTest {

  @Test
  void noRequests() throws IOException {

    GetFirstBatchComputeRequest<Compute.Images.Get, Image> batchRequest =
        GetFirstBatchComputeRequest.create(new FakeBatchComputeRequest<>());

    Optional<Image> result = batchRequest.execute("batchContext");

    assertThat(result).isEmpty();
  }

  @Test
  void returnsFirstValidResponse() throws IOException {

    GetFirstBatchComputeRequest<Compute.Images.Get, Image> batchRequest =
        GetFirstBatchComputeRequest.create(new FakeBatchComputeRequest<>());
    batchRequest.queue(FakeGoogleComputeRequest.createWithException(new IOException("one")));
    batchRequest.queue(FakeGoogleComputeRequest.createWithException(new IOException("two")));
    batchRequest.queue(FakeGoogleComputeRequest.createWithException(new IOException("three")));
    batchRequest.queue(FakeGoogleComputeRequest.createWithException(new IOException("four")));
    batchRequest.queue(FakeGoogleComputeRequest.createWithResponse(new Image().setName("five")));
    batchRequest.queue(FakeGoogleComputeRequest.createWithResponse(new Image().setName("six")));
    batchRequest.queue(FakeGoogleComputeRequest.createWithResponse(new Image().setName("seven")));
    batchRequest.queue(FakeGoogleComputeRequest.createWithException(new IOException("eight")));
    batchRequest.queue(FakeGoogleComputeRequest.createWithException(new IOException("nine")));
    batchRequest.queue(FakeGoogleComputeRequest.createWithException(new IOException("ten")));

    Optional<Image> result = batchRequest.execute("batchContext");

    assertThat(result).map(Image::getName).hasValue("five");
  }

  @Test
  void notFound() throws IOException {

    GetFirstBatchComputeRequest<Compute.Images.Get, Image> batchRequest =
        GetFirstBatchComputeRequest.create(new FakeBatchComputeRequest<>());
    HttpResponseException notFoundException =
        GoogleJsonResponseExceptionFactoryTesting.newMock(
            GsonFactory.getDefaultInstance(), 404, "not found");
    batchRequest.queue(FakeGoogleComputeRequest.createWithException(notFoundException));
    batchRequest.queue(FakeGoogleComputeRequest.createWithException(notFoundException));
    batchRequest.queue(FakeGoogleComputeRequest.createWithException(notFoundException));
    batchRequest.queue(FakeGoogleComputeRequest.createWithException(notFoundException));

    Optional<Image> result = batchRequest.execute("batchContext");

    assertThat(result).isEmpty();
  }

  @Test
  void error() throws IOException {

    GetFirstBatchComputeRequest<Compute.Images.Get, Image> batchRequest =
        GetFirstBatchComputeRequest.create(new FakeBatchComputeRequest<>());
    HttpResponseException notFoundException =
        GoogleJsonResponseExceptionFactoryTesting.newMock(
            GsonFactory.getDefaultInstance(), 404, "not found");
    HttpResponseException actualError =
        GoogleJsonResponseExceptionFactoryTesting.newMock(
            GsonFactory.getDefaultInstance(), 500, "bad news");
    batchRequest.queue(FakeGoogleComputeRequest.createWithException(notFoundException));
    batchRequest.queue(FakeGoogleComputeRequest.createWithException(notFoundException));
    batchRequest.queue(FakeGoogleComputeRequest.createWithException(actualError));
    batchRequest.queue(FakeGoogleComputeRequest.createWithException(notFoundException));

    assertThatThrownBy(() -> batchRequest.execute("batchContext")).hasMessageContaining("bad news");
  }
}
