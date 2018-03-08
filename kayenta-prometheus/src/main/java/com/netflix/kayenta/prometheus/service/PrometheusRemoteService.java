/*
 * Copyright 2017 Google, Inc.
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

package com.netflix.kayenta.prometheus.service;

import com.netflix.kayenta.prometheus.model.PrometheusMetricDescriptorsResponse;
import com.netflix.kayenta.prometheus.model.PrometheusResults;
import retrofit.http.GET;
import retrofit.http.Query;

import java.util.List;

public interface PrometheusRemoteService {

  // See https://prometheus.io/docs/querying/api/#range-queries
  @GET("/api/v1/query_range")
  List<PrometheusResults> rangeQuery(@Query("query") String query,
                                     @Query("start") String start,
                                     @Query("end") String end,
                                     @Query("step") Long step);

  // See https://prometheus.io/docs/querying/api/#range-queries
  @GET("/api/v1/label/__name__/values")
  PrometheusMetricDescriptorsResponse listMetricDescriptors();
}
