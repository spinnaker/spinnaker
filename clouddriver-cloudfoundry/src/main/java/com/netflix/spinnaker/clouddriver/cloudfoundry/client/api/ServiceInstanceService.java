/*
 * Copyright 2018 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.cloudfoundry.client.api;

import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2.*;
import retrofit.client.Response;
import retrofit.http.*;

import java.util.List;

public interface ServiceInstanceService {
  @GET("/v2/spaces/{guid}/service_instances")
  Page<ServiceInstance> all(@Query("page") Integer page, @Path("guid") String spaceGuid, @Query("q") String queryParam);

  @POST("/v2/service_bindings?accepts_incomplete=true")
  Response createServiceBinding(@Body CreateServiceBinding body);

  @GET("/v2/spaces/{guid}/service_instances")
  Page<ServiceInstance> findAllServiceInstancesBySpaceId(@Path("guid") String spaceGuid, @Query("page") Integer page, @Query("q") List<String> queryParams);

  @GET("/v2/services")
  Page<Service> findService(@Query("page") Integer page, @Query("q") List<String> queryParams);

  @GET("/v2/service_plans")
  Page<ServicePlan> findServicePlans(@Query("page") Integer page, @Query("q") List<String> queryParams);

  @POST("/v2/service_instances?accepts_incomplete=false")
  Response createServiceInstance(@Body CreateServiceInstance body);

  @PUT("/v2/service_instances/{guid}?accepts_incomplete=false")
  Response updateServiceInstance(@Path("guid") String serviceInstanceGuid, @Body CreateServiceInstance body);

  @GET("/v2/service_instances/{guid}/service_bindings")
  Page<ServiceBinding> getBindingsForServiceInstance(@Path("guid") String serviceInstanceGuid, @Query("page") Integer page, @Query("q") List<String> queryParams);

  @DELETE("/v2/service_instances/{guid}?accepts_incomplete=false")
  Response deleteServiceInstance(@Path("guid") String serviceInstanceGuid);
}
