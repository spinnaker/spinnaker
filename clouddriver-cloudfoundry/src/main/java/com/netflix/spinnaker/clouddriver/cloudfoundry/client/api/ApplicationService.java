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

import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.CreateApplication;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2.ApplicationEnv;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2.InstanceStatus;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2.MapRoute;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.*;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.Package;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.Process;
import retrofit.client.Response;
import retrofit.http.*;
import retrofit.mime.TypedFile;

import java.util.List;
import java.util.Map;

public interface ApplicationService {
  @GET("/v3/apps")
  Pagination<Application> all(@Query("page") Integer page, @Query("names") List<String> names, @Query("space_guids") List<String> spaceGuids);

  @GET("/v3/apps/{guid}")
  Application findById(@Path("guid") String guid);

  @GET("/v2/apps/{guid}/env")
  ApplicationEnv findApplicationEnvById(@Path("guid") String guid);

  @GET("/v3/apps/{guid}/droplets/current")
  Droplet findDropletByApplicationGuid(@Path("guid") String guid);

  @GET("/v2/apps/{guid}/instances")
  Map<String, InstanceStatus> instances(@Path("guid") String guid);

  /**
   * Requires an empty body.
   */
  @PUT("/v2/apps/{aguid}/routes/{rguid}")
  Response mapRoute(@Path("aguid") String applicationGuid, @Path("rguid") String routeGuid, @Body MapRoute body);

  @DELETE("/v2/apps/{aguid}/routes/{rguid}")
  Response unmapRoute(@Path("aguid") String applicationGuid, @Path("rguid") String routeGuid);

  @POST("/v3/apps/{guid}/actions/start")
  Response startApplication(@Path("guid") String guid, @Body StartApplication body);

  @POST("/v3/apps/{guid}/actions/stop")
  Response stopApplication(@Path("guid") String guid, @Body StopApplication body);

  @DELETE("/v3/apps/{guid}")
  Response deleteApplication(@Path("guid") String guid);

  @DELETE("/v2/apps/{guid}/instances/{index}")
  Response deleteAppInstance(@Path("guid") String guid, @Path("index") String index);

  @POST("/v3/processes/{guid}/actions/scale")
  Response scaleApplication(@Path("guid") String guid, @Body ScaleApplication scaleApplication);

  @GET("/v3/processes/{guid}")
  Process findProcessById(@Path("guid") String guid);

  @GET("/v3/processes/{guid}/stats")
  ProcessResources findProcessStatsById(@Path("guid") String guid);

  @POST("/v3/apps")
  Application createApplication(@Body CreateApplication application);

  @GET("/v3/apps/{guid}/packages")
  Pagination<Package> findPackagesByAppId(@Path("guid") String appGuid);

  @POST("/v3/packages")
  Package createPackage(@Body CreatePackage pkg);

  @GET("/v3/packages/{guid}")
  Package getPackage(@Path("guid") String packageGuid);

  @GET("/v3/packages/{guid}/download")
  Response downloadPackage(@Path("guid") String packageGuid);

  @Multipart
  @POST("/v3/packages/{guid}/upload")
  Package uploadPackageBits(@Path("guid") String packageGuid, @Part("bits") TypedFile file);

  @POST("/v3/builds")
  Build createBuild(@Body CreateBuild build);

  @GET("/v3/builds/{guid}")
  Build getBuild(@Path("guid") String buildGuid);

  @PATCH("/v3/apps/{guid}/relationships/current_droplet")
  Response setCurrentDroplet(@Path("guid") String appGuid, @Body ToOneRelationship body);
}
