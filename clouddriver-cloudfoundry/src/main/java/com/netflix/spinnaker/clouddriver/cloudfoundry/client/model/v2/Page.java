/*
 * Copyright 2018 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2;

import lombok.Data;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Data
public class Page<R> {
  private int totalResults;
  private int totalPages;
  private List<Resource<R>> resources = Collections.emptyList();

  public static <R> Page<R> singleton(R data, String resourceId) {
    Page<R> page = new Page<>();
    page.setTotalPages(1);
    page.setTotalResults(1);

    Resource.Metadata metadata = new Resource.Metadata();
    metadata.setGuid(resourceId);

    Resource<R> resource = new Resource<>();
    resource.setMetadata(metadata);
    resource.setEntity(data);

    page.setResources(Collections.singletonList(resource));

    return page;
  }

  public static <R> Page<R> asPage(R... data) {
    Page<R> page = new Page<>();
    page.setTotalPages(1);
    page.setTotalResults(data.length);

    page.setResources(Arrays.stream(data)
      .map(d -> {
        Resource.Metadata metadata = new Resource.Metadata();
        metadata.setGuid(UUID.randomUUID().toString());

        Resource<R> resource = new Resource<>();
        resource.setMetadata(metadata);
        resource.setEntity(d);
        return resource;
      })
      .collect(Collectors.toList())
    );

    return page;
  }
}
