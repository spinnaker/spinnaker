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
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.CreateSharedServiceInstances;
import java.util.List;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.*;

public interface ServiceInstanceService {
  @GET("/v2/service_instances")
  Call<Page<ServiceInstance>> all(
      @Query("page") Integer page, @Query("q") List<String> queryParams);

  @GET("/v2/user_provided_service_instances")
  Call<Page<UserProvidedServiceInstance>> allUserProvided(
      @Query("page") Integer page, @Query("q") List<String> queryParam);

  @GET("/v2/services")
  Call<Page<Service>> findService(
      @Query("page") Integer page, @Query("q") List<String> queryParams);

  @GET("/v2/service_plans/{guid}")
  Call<Resource<ServicePlan>> findServicePlanByServicePlanId(@Path("guid") String servicePlanGuid);

  @GET("/v2/services/{guid}")
  Call<Resource<Service>> findServiceByServiceId(@Path("guid") String serviceGuid);

  @GET("/v2/spaces/{guid}/services")
  Call<Page<Service>> findServiceBySpaceId(
      @Path("guid") String spaceGuid,
      @Query("page") Integer page,
      @Query("q") List<String> queryParams);

  @GET("/v2/service_plans")
  Call<Page<ServicePlan>> findServicePlans(
      @Query("page") Integer page, @Query("q") List<String> queryParams);

  @POST("/v2/service_instances?accepts_incomplete=true")
  Call<Resource<ServiceInstance>> createServiceInstance(@Body CreateServiceInstance body);

  @POST("/v2/user_provided_service_instances")
  Call<Resource<UserProvidedServiceInstance>> createUserProvidedServiceInstance(
      @Body CreateUserProvidedServiceInstance body);

  @PUT("/v2/service_instances/{guid}?accepts_incomplete=true")
  Call<Resource<ServiceInstance>> updateServiceInstance(
      @Path("guid") String serviceInstanceGuid, @Body CreateServiceInstance body);

  @PUT("/v2/user_provided_service_instances/{guid}")
  Call<Resource<UserProvidedServiceInstance>> updateUserProvidedServiceInstance(
      @Path("guid") String userProvidedServiceInstanceGuid,
      @Body CreateUserProvidedServiceInstance body);

  @POST("/v2/service_bindings?accepts_incomplete=true")
  Call<Resource<ServiceBinding>> createServiceBinding(@Body CreateServiceBinding body);

  @GET("/v2/service_instances/{guid}/service_bindings")
  Call<Page<ServiceBinding>> getBindingsForServiceInstance(
      @Path("guid") String serviceInstanceGuid,
      @Query("page") Integer page,
      @Query("q") List<String> queryParams);

  @GET("/v2/user_provided_service_instances/{guid}/service_bindings")
  Call<Page<ServiceBinding>> getBindingsForUserProvidedServiceInstance(
      @Path("guid") String userProvidedServiceInstanceGuid,
      @Query("page") Integer page,
      @Query("q") List<String> queryParams);

  @GET("/v2/service_bindings")
  Call<Page<ServiceBinding>> getAllServiceBindings(@Query("q") List<String> queryParams);

  @DELETE("/v2/service_bindings/{guid}?accepts_incomplete=true")
  Call<ResponseBody> deleteServiceBinding(@Path("guid") String serviceBindingGuid);

  @DELETE("/v2/service_instances/{guid}?accepts_incomplete=true")
  Call<ResponseBody> destroyServiceInstance(@Path("guid") String serviceInstanceGuid);

  @DELETE("/v2/user_provided_service_instances/{guid}")
  Call<ResponseBody> destroyUserProvidedServiceInstance(@Path("guid") String serviceInstanceGuid);

  @POST("/v3/service_instances/{guid}/relationships/shared_spaces")
  Call<ResponseBody> shareServiceInstanceToSpaceIds(
      @Path("guid") String serviceInstanceGuid, @Body CreateSharedServiceInstances body);

  @GET("/v3/service_instances/{guid}/relationships/shared_spaces")
  Call<SharedTo> getShareServiceInstanceSpaceIdsByServiceInstanceId(
      @Path("guid") String serviceInstanceGuid);

  @DELETE("/v3/service_instances/{guid}/relationships/shared_spaces/{space_guid}")
  Call<ResponseBody> unshareServiceInstanceFromSpaceId(
      @Path("guid") String serviceInstanceGuid, @Path("space_guid") String spaceGuid);
}
