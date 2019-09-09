/*
 *
 * Copyright 2019 Netflix, Inc.
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
 *
 */
package com.netflix.spinnaker.gate.services.internal;

import com.netflix.spinnaker.kork.manageddelivery.model.DeliveryConfig;
import com.netflix.spinnaker.kork.manageddelivery.model.Resource;
import java.util.List;
import java.util.Map;
import retrofit.http.Body;
import retrofit.http.DELETE;
import retrofit.http.GET;
import retrofit.http.POST;
import retrofit.http.Path;
import retrofit.http.Query;

public interface KeelService {

  @GET("/resources/events/{name}")
  List<Map<String, Object>> getResourceEvents(
      @Path("name") String name, @Query("limit") Integer limit);

  @GET("/resources/{name}")
  Resource getResource(@Path("name") String name);

  @POST("/resources")
  Resource upsertResource(@Body Resource resource);

  @DELETE("/resources/{name}")
  Resource deleteResource(@Path("name") String name);

  @GET("/delivery-configs/{name}")
  DeliveryConfig getManifest(@Path("name") String name);

  @POST("/delivery-configs")
  DeliveryConfig upsertManifest(@Body DeliveryConfig manifest);

  @GET("/application/{application}")
  Map getApplicationDetails(
      @Path("application") String application, @Query("includeDetails") Boolean includeDetails);
}
