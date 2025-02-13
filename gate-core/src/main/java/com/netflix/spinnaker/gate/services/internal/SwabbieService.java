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

package com.netflix.spinnaker.gate.services.internal;

import com.netflix.spinnaker.kork.plugins.SpinnakerPluginDescriptor;
import java.util.List;
import java.util.Map;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Headers;
import retrofit2.http.PUT;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface SwabbieService {
  @Headers("Accept: application/json")
  @PUT("/resources/state/{namespace}/{resourceId}/optOut")
  Call<Map> optOut(
      @Path("namespace") String namespace,
      @Path("resourceId") String resourceId,
      @Body String ignored);

  @Headers("Accept: application/json")
  @PUT("/resources/state/{namespace}/{resourceId}/restore")
  Call<Void> restore(
      @Path("namespace") String namespace,
      @Path("resourceId") String resourceId,
      @Body String ignored);

  @Headers("Accept: application/json")
  @GET("/resources/marked/{namespace}/{resourceId}")
  Call<Map> get(@Path("namespace") String namespace, @Path("resourceId") String resourceId);

  @Headers("Accept: application/json")
  @GET("/resources/marked")
  Call<List> getMarkedList(@Query("list") Boolean list);

  @Headers("Accept: application/json")
  @GET("/resources/deleted")
  Call<List> getDeletedList();

  @GET("/installedPlugins")
  Call<List<SpinnakerPluginDescriptor>> getInstalledPlugins();
}
