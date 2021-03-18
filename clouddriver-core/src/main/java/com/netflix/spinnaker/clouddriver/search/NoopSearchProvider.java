package com.netflix.spinnaker.clouddriver.search;

import java.util.List;
import java.util.Map;

public class NoopSearchProvider implements SearchProvider {
  @Override
  public String getPlatform() {
    return "noop";
  }

  @Override
  public SearchResultSet search(String query, Integer pageNumber, Integer pageSize) {
    return empty(query, pageNumber, pageSize);
  }

  @Override
  public SearchResultSet search(
      String query, Integer pageNumber, Integer pageSize, Map<String, String> filters) {
    return empty(query, pageNumber, pageSize);
  }

  @Override
  public SearchResultSet search(
      String query, List<String> types, Integer pageNumber, Integer pageSize) {
    return empty(query, pageNumber, pageSize);
  }

  @Override
  public SearchResultSet search(
      String query,
      List<String> types,
      Integer pageNumber,
      Integer pageSize,
      Map<String, String> filters) {
    return empty(query, pageNumber, pageSize);
  }

  private static SearchResultSet empty(String query, Integer pageNumger, Integer pageSize) {
    return SearchResultSet.builder()
        .totalMatches(0)
        .platform("noop")
        .pageNumber(pageNumger)
        .pageSize(pageSize)
        .query(query)
        .build();
  }
}
