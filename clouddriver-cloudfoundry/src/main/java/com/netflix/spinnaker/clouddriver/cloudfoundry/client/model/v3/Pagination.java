package com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Data
public class Pagination<R> {
  private Details pagination;
  private List<R> resources;

  @Getter
  @Setter
  public static class Details {
    private int totalPages;
  }
}
