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

import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.Map;

/** A Searchable component provides a mechanism to query for a collection of items */
public interface SearchProvider {
  /**
   * Returns the platform the search provider services
   *
   * @return a String, e.g. 'aws', 'gce'
   */
  String getPlatform();

  /**
   * Finds all matching items for the provided query
   *
   * @param query a query string
   * @param pageNumber page index (1-based) of the result set
   * @param pageSize number of items per page
   * @return a list of matched items
   */
  SearchResultSet search(String query, Integer pageNumber, Integer pageSize);

  /**
   * Finds all matching items for the provided query, filtered by the supplied filters
   *
   * @param query a query string
   * @param pageNumber page index (1-based) of the result set
   * @param pageSize number of items per page
   * @param filters a map of inclusive filters
   * @return a list of matched items
   */
  SearchResultSet search(
      String query, Integer pageNumber, Integer pageSize, Map<String, String> filters);

  /**
   * Finds all matching items for the provided query and type
   *
   * @param query a query string
   * @param types the types of items to search for
   * @param pageNumber page index (1-based) of the result set
   * @param pageSize number of items per page
   * @return a list of matched items
   */
  SearchResultSet search(String query, List<String> types, Integer pageNumber, Integer pageSize);

  /**
   * Finds all matching items for the provided query and type, filtered by the supplied filters
   *
   * @param query a query string
   * @param types the types of items to search for
   * @param pageNumber page index (1-based) of the result set
   * @param pageSize number of items per page
   * @param filters a map of inclusive filters
   * @return a list of matched items
   */
  SearchResultSet search(
      String query,
      List<String> types,
      Integer pageNumber,
      Integer pageSize,
      Map<String, String> filters);

  /**
   * Provides a list of filter keys to be removed prior to searching
   *
   * @return a list of filter keys to optionally be removed prior to searching
   */
  default List<String> excludedFilters() {
    return ImmutableList.of();
  }
}
