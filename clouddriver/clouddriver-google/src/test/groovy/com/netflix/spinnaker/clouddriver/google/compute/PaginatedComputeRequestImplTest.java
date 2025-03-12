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

import com.google.api.services.compute.Compute;
import com.google.api.services.compute.model.Image;
import com.google.api.services.compute.model.ImageList;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class PaginatedComputeRequestImplTest {

  @Test
  void execute() throws IOException {

    ImageListRequestGenerator requestGenerator = new ImageListRequestGenerator();
    requestGenerator.itemPrefix = "myImage-";
    requestGenerator.itemsPerPage = 2;
    requestGenerator.pages = 3;

    PaginatedComputeRequestImpl<Compute.Images.List, ImageList, Image> request =
        new PaginatedComputeRequestImpl<>(
            requestGenerator, ImageList::getNextPageToken, ImageList::getItems);

    ImmutableList<Image> result = request.execute();

    assertThat(result)
        .extracting(Image::getName)
        .containsExactly(
            "myImage-1", "myImage-2", "myImage-3", "myImage-4", "myImage-5", "myImage-6");
  }

  @Test
  void nullItems() throws IOException {

    PaginatedComputeRequestImpl<Compute.Images.List, ImageList, Image> request =
        new PaginatedComputeRequestImpl<>(
            pageToken ->
                FakeGoogleComputeRequest.createWithResponse(
                    new ImageList().setItems(null), mock(Compute.Images.List.class)),
            ImageList::getNextPageToken,
            ImageList::getItems);

    ImmutableList<Image> result = request.execute();

    assertThat(result).isEmpty();
  }

  @Test
  void exception() {

    PaginatedComputeRequestImpl<Compute.Images.List, ImageList, Image> request =
        new PaginatedComputeRequestImpl<>(
            pageToken ->
                FakeGoogleComputeRequest.createWithException(
                    new IOException("bad news"), mock(Compute.Images.List.class)),
            ImageList::getNextPageToken,
            ImageList::getItems);

    assertThatThrownBy(request::execute).hasMessageContaining("bad news");
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
