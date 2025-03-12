package com.netflix.spinnaker.orca.clouddriver.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
public class SearchResultSet {
  /** The total number of items matching the search criteria (query, platform, and type) */
  Integer totalMatches;

  /** The page index (1-based) of the result set */
  Integer pageNumber;

  /** The number of items per page */
  Integer pageSize;

  /** The platform of results the provider supplies - e.g. "aws", "gce", etc. */
  String platform;

  /** The original query string, used to sort results */
  String query;

  /** The paginated list of objects matching the query */
  List<Map<String, Object>> results = new ArrayList<>();
}
