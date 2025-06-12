/*
 * Copyright 2018 Snap Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.kayenta.graphite.service;

import com.netflix.kayenta.graphite.model.GraphiteMetricDescriptorsResponse;
import com.netflix.kayenta.graphite.model.GraphiteResults;
import java.util.List;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface GraphiteRemoteService {

  // From https://graphite.readthedocs.io/en/1.1.2/render_api.html#
  @GET("render")
  List<GraphiteResults> rangeQuery(
      @Query(value = "target") String target,
      @Query("from") long from,
      @Query("until") long until,
      @Query("format") String format);

  @GET("metrics/find")
  GraphiteMetricDescriptorsResponse findMetrics(
      @Query("query") String query, @Query("format") String format);
}
