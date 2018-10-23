/*
 * Copyright 2018 Armory, Inc.
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

package com.netflix.kayenta.datadog.service;

import com.netflix.kayenta.model.DatadogMetricDescriptorsResponse;
import retrofit.http.GET;
import retrofit.http.Query;

public interface DatadogRemoteService {

  //See https://docs.datadoghq.com/api/?lang=python#query-time-series-points
  @GET("/api/v1/query")
  DatadogTimeSeries getTimeSeries(@Query("api_key") String apiKey,
                                  @Query("application_key") String applicationKey,
                                  @Query("from") int startTimestamp,
                                  @Query("to") int endTimestamp,
                                  @Query("query") String query);

  @GET("/api/v1/metrics")
  DatadogMetricDescriptorsResponse getMetrics(@Query("api_key") String apiKey,
                                              @Query("application_key") String applicationKey,
                                              @Query("from") long from);
}
