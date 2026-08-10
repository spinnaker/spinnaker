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

import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.*;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.Application;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.ApplicationEnv;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.Package;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.Process;
import java.util.List;
import okhttp3.MultipartBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.*;

public interface ApplicationService {
  @GET("v3/apps")
  Call<Pagination<Application>> all(
      @Query("page") Integer page,
      @Query("per_page") Integer perPage,
      @Query("names") List<String> names,
      @Query("space_guids") String spaceGuids);

  @GET("v3/apps/{guid}")
  Call<Application> findById(@Path("guid") String guid);

  @GET("v3/apps/{guid}/env")
  Call<ApplicationEnv> findApplicationEnvById(@Path("guid") String guid);

  @GET("v3/apps/{guid}/droplets/current")
  Call<Droplet> findDropletByApplicationGuid(@Path("guid") String guid);

  @GET("v3/apps/{guid}/processes/web/stats")
  Call<ProcessResources> findWebProcessStats(@Path("guid") String guid);

  @GET("v3/apps/{guid}/processes/web")
  Call<Process> findWebProcess(@Path("guid") String guid);

  /**
   * Route mapping in CAPI v3 is done by creating/removing "destinations" on the route, rather than
   * a v2 app<->route association. These are declared here (instead of on RouteService) since the
   * only current consumer, {@code Applications.mapRoute}/{@code unmapRoute}, only has an
   * ApplicationService dependency.
   */
  @POST("v3/routes/{rguid}/destinations")
  Call<Destination.Page> addRouteDestination(
      @Path("rguid") String routeGuid, @Body Destination.Page body);

  @GET("v3/routes/{rguid}/destinations")
  Call<Destination.Page> listRouteDestinations(@Path("rguid") String routeGuid);

  @DELETE("v3/routes/{rguid}/destinations/{dguid}")
  Call<ResponseBody> removeRouteDestination(
      @Path("rguid") String routeGuid, @Path("dguid") String destinationGuid);

  @POST("v3/apps/{guid}/actions/start")
  Call<ResponseBody> startApplication(@Path("guid") String guid, @Body StartApplication body);

  @POST("v3/apps/{guid}/actions/stop")
  Call<ResponseBody> stopApplication(@Path("guid") String guid, @Body StopApplication body);

  @DELETE("v3/apps/{guid}")
  Call<ResponseBody> deleteApplication(@Path("guid") String guid);

  @POST("v3/apps")
  Call<Application> createApplication(@Body CreateApplication application);

  @GET("v3/apps/{guid}/packages")
  Call<Pagination<Package>> findPackagesByAppId(@Path("guid") String appGuid);

  @POST("v3/packages")
  Call<Package> createPackage(@Body CreatePackage pkg);

  @GET("v3/packages/{guid}")
  Call<Package> getPackage(@Path("guid") String packageGuid);

  @GET("v3/packages/{guid}/download")
  Call<ResponseBody> downloadPackage(@Path("guid") String packageGuid);

  @Multipart
  @POST("v3/packages/{guid}/upload")
  Call<Package> uploadPackageBits(@Path("guid") String packageGuid, @Part MultipartBody.Part file);

  @POST("v3/builds")
  Call<Build> createBuild(@Body CreateBuild build);

  @GET("v3/builds/{guid}")
  Call<Build> getBuild(@Path("guid") String buildGuid);

  @PATCH("v3/apps/{guid}/relationships/current_droplet")
  Call<ResponseBody> setCurrentDroplet(@Path("guid") String appGuid, @Body ToOneRelationship body);

  @POST("v3/apps/{guid}/actions/restage")
  Call<ResponseBody> restageApplication(@Path("guid") String appGuid);

  @GET("v3/service_credential_bindings")
  Call<Pagination<ServiceCredentialBinding>> getServiceBindings(
      @Query("app_guids") String appGuid, @Query("type") String type);
}
