/*
 * Copyright 2015 Netflix, Inc.
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
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
