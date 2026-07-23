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

import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.CreateServiceCredentialBinding;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.CreateServiceInstance;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.CreateSharedServiceInstances;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.CreateUserProvidedServiceInstance;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.Pagination;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.ServiceCredentialBinding;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.ServiceInstance;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.ServiceOffering;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.ServicePlan;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.SharedTo;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.*;

public interface ServiceInstanceService {
  @GET("v3/service_instances")
  Call<Pagination<ServiceInstance>> all(
      @Query("page") Integer page,
      @Query("names") String names,
      @Query("space_guids") String spaceGuids,
      @Query("type") String type);

  @GET("v3/service_offerings")
  Call<Pagination<ServiceOffering>> findServiceOfferings(
      @Query("page") Integer page, @Query("names") String names);

  @GET("v3/service_offerings/{guid}")
  Call<ServiceOffering> findServiceOfferingByGuid(@Path("guid") String serviceOfferingGuid);

  @GET("v3/service_plans/{guid}")
  Call<ServicePlan> findServicePlanByServicePlanId(@Path("guid") String servicePlanGuid);

  /**
   * {@code space_guids} performs server-side visibility resolution: it returns every plan visible
   * to that space (public, org-scoped, or space-scoped), which is the v3 replacement for v2's
   * {@code GET /v2/spaces/{guid}/services}. {@code service_offering_guids} instead scopes to all
   * plans under one offering, for looking up plans by service name.
   */
  @GET("v3/service_plans")
  Call<Pagination<ServicePlan>> findServicePlans(
      @Query("page") Integer page,
      @Query("service_offering_guids") String serviceOfferingGuid,
      @Query("space_guids") String spaceGuid);

  @POST("v3/service_instances")
  Call<ResponseBody> createServiceInstance(@Body CreateServiceInstance body);

  @POST("v3/service_instances")
  Call<ServiceInstance> createUserProvidedServiceInstance(
      @Body CreateUserProvidedServiceInstance body);

  @PATCH("v3/service_instances/{guid}")
  Call<ResponseBody> updateServiceInstance(
      @Path("guid") String serviceInstanceGuid, @Body CreateServiceInstance body);

  @PATCH("v3/service_instances/{guid}")
  Call<ServiceInstance> updateUserProvidedServiceInstance(
      @Path("guid") String userProvidedServiceInstanceGuid,
      @Body CreateUserProvidedServiceInstance body);

  @POST("v3/service_credential_bindings")
  Call<ResponseBody> createServiceBinding(@Body CreateServiceCredentialBinding body);

  @GET("v3/service_credential_bindings")
  Call<Pagination<ServiceCredentialBinding>> getServiceBindings(
      @Query("page") Integer page,
      @Query("service_instance_guids") String serviceInstanceGuid,
      @Query("app_guids") String appGuid,
      @Query("type") String type);

  @DELETE("v3/service_credential_bindings/{guid}")
  Call<ResponseBody> deleteServiceBinding(@Path("guid") String serviceBindingGuid);

  @DELETE("v3/service_instances/{guid}?purge=false")
  Call<ResponseBody> destroyServiceInstance(@Path("guid") String serviceInstanceGuid);

  @POST("v3/service_instances/{guid}/relationships/shared_spaces")
  Call<ResponseBody> shareServiceInstanceToSpaceIds(
      @Path("guid") String serviceInstanceGuid, @Body CreateSharedServiceInstances body);

  @GET("v3/service_instances/{guid}/relationships/shared_spaces")
  Call<SharedTo> getShareServiceInstanceSpaceIdsByServiceInstanceId(
      @Path("guid") String serviceInstanceGuid);

  @DELETE("v3/service_instances/{guid}/relationships/shared_spaces/{space_guid}")
  Call<ResponseBody> unshareServiceInstanceFromSpaceId(
      @Path("guid") String serviceInstanceGuid, @Path("space_guid") String spaceGuid);
}
