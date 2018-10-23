/*
 * Copyright 2018 Netflix, Inc.
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
