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
import static org.mockito.Mockito.mock;

import com.google.api.client.googleapis.testing.json.GoogleJsonResponseExceptionFactoryTesting;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.compute.Compute;
import com.google.api.services.compute.model.Image;
import com.google.api.services.compute.model.ImageList;
import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class BatchPaginatedComputeRequestImplTest {

  @Test
  void execute() throws IOException {

    BatchPaginatedComputeRequestImpl<Compute.Images.List, ImageList, Image> batchRequest =
        new BatchPaginatedComputeRequestImpl<>(FakeBatchComputeRequest::new);

    ImageListRequestGenerator set1 = new ImageListRequestGenerator();
    set1.itemPrefix = "set1-";
    set1.itemsPerPage = 2;
    set1.pages = 3;
    ImageListRequestGenerator set2 = new ImageListRequestGenerator();
    set2.itemPrefix = "noPages-";
    set2.itemsPerPage = 2;
    set2.pages = 0;
    ImageListRequestGenerator set3 = new ImageListRequestGenerator();
    set3.itemPrefix = "set3-";
    set3.itemsPerPage = 4;
    set3.pages = 1;

    batchRequest.queue(
        new PaginatedComputeRequestImpl<>(set1, ImageList::getNextPageToken, ImageList::getItems));
    batchRequest.queue(
        new PaginatedComputeRequestImpl<>(set2, ImageList::getNextPageToken, ImageList::getItems));
    batchRequest.queue(
        new PaginatedComputeRequestImpl<>(set3, ImageList::getNextPageToken, ImageList::getItems));

    ImmutableSet<Image> result = batchRequest.execute("batchContext");

    assertThat(result)
        .extracting(Image::getName)
        .containsExactlyInAnyOrder(
            "set1-1", "set1-2", "set1-3", "set1-4", "set1-5", "set1-6", "set3-1", "set3-2",
            "set3-3", "set3-4");
  }

  @Test
  void nullItems() throws IOException {

    BatchPaginatedComputeRequestImpl<Compute.Images.List, ImageList, Image> batchRequest =
        new BatchPaginatedComputeRequestImpl<>(FakeBatchComputeRequest::new);
    batchRequest.queue(
        new PaginatedComputeRequestImpl<>(
            pageToken ->
                FakeGoogleComputeRequest.createWithResponse(
                    new ImageList().setItems(null), mock(Compute.Images.List.class)),
            ImageList::getNextPageToken,
            ImageList::getItems));

    ImmutableSet<Image> result = batchRequest.execute("batchContext");

    assertThat(result).isEmpty();
  }

  @Test
  void exception() {

    BatchPaginatedComputeRequestImpl<Compute.Images.List, ImageList, Image> batchRequest =
        new BatchPaginatedComputeRequestImpl<>(FakeBatchComputeRequest::new);
    batchRequest.queue(
        new PaginatedComputeRequestImpl<>(
            pageToken ->
                FakeGoogleComputeRequest.createWithException(
                    GoogleJsonResponseExceptionFactoryTesting.newMock(
                        GsonFactory.getDefaultInstance(), 500, "bad news"),
                    mock(Compute.Images.List.class)),
            ImageList::getNextPageToken,
            ImageList::getItems));

    assertThatThrownBy(() -> batchRequest.execute("batchContext")).hasMessageContaining("bad news");
  }

  private static class ImageListRequestGenerator
      implements PaginatedComputeRequestImpl.RequestGenerator<Compute.Images.List, ImageList> {

    String itemPrefix;
    int itemsPerPage;
    int pages;

    @Override
    public GoogleComputeRequest<Compute.Images.List, ImageList> createRequest(String pageToken) {

      int pageNum = 0;
      if (!pageToken.isEmpty()) {
        pageNum = Integer.parseInt(pageToken);
      }
      if (pageNum == 0 && pages == 0) {
        return FakeGoogleComputeRequest.createWithResponse(
            new ImageList(), mock(Compute.Images.List.class));
      }
      if (pageNum >= pages) {
        throw new AssertionError("requested too many pages");
      }

      List<Image> items = new ArrayList<>();
      for (int i = 1; i <= itemsPerPage; ++i) {
        items.add(new Image().setName(itemPrefix + (pageNum * itemsPerPage + i)));
      }
      ImageList response = new ImageList().setItems(items);

      if (pageNum < pages - 1) {
        response.setNextPageToken(Integer.toString(pageNum + 1));
      }

      return FakeGoogleComputeRequest.createWithResponse(response, mock(Compute.Images.List.class));
    }
  }
}
