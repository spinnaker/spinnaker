package com.netflix.spinnaker.clouddriver.search;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SearchQueryCommand {
  /**
   * the phrase to query
   */
  String q;

  /**
   * (optional) a filter, used to only return results of that type. If no value is supplied, all types will be returned
   */
  List<String> type;

  /**
   * a filter, used to only return results from providers whose platform value matches this
   */
  String platform = "";

  /**
   * the page number, starting with 1
   */
  Integer page = 1;

  /**
   * the maximum number of results to return per page
   */
  Integer pageSize = 10;

  /**
   * (optional) a map of ad-hoc key-value pairs to further filter the keys,
   * based on the map provided by {@link com.netflix.spinnaker.oort.aws.data.Keys#parse(java.lang.String)}
   * potential matches must fully intersect the filter map entries
   */
  Map<String, String> filters;
}
