/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.orca.clouddriver;

import java.util.Map;
import retrofit.client.Response;
import retrofit.http.Body;
import retrofit.http.POST;
import retrofit.http.PUT;
import retrofit.http.Path;

public interface CloudDriverCacheService {

  @POST("/cache/{cloudProvider}/{type}")
  Response forceCacheUpdate(
      @Path("cloudProvider") String cloudProvider,
      @Path("type") String type,
      @Body Map<String, ?> data);

  @PUT("/admin/db/truncate/{namespace}")
  Map<String, Object> clearNamespace(@Path("namespace") String namespace);
}
