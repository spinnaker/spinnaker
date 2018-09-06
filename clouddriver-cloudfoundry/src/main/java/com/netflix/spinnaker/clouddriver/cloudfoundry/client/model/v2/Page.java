package com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2;

import lombok.Data;

import java.util.Collections;
import java.util.List;

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
}
