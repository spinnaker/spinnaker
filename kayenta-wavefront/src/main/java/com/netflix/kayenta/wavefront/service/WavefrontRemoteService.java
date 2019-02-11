/*
 * Copyright 2019 Intuit, Inc.
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
package com.netflix.kayenta.wavefront.service;

import retrofit.http.GET;
import retrofit.http.Header;
import retrofit.http.Query;

import java.util.List;
import java.util.Map;

public interface WavefrontRemoteService {
    @GET("/api/v2/chart/api")
    WavefrontTimeSeries fetch(@Header("Authorization") String authorization,
                          @Query("n") String name,
                          @Query("q") String query,
                          @Query("s") Long startTime,
                          @Query("e") Long endTime,
                          @Query("g") String granularity,
                          @Query("summarization") String summarization,
                          @Query("listMode") boolean listMode,
                          @Query("strict") boolean strict,
                          @Query("sorted") boolean sorted);
}
