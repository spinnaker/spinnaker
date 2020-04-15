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

import com.netflix.spinnaker.gate.model.manageddelivery.ConstraintState;
import com.netflix.spinnaker.gate.model.manageddelivery.ConstraintStatus;
import com.netflix.spinnaker.gate.model.manageddelivery.DeliveryConfig;
import com.netflix.spinnaker.gate.model.manageddelivery.EnvironmentArtifactPin;
import com.netflix.spinnaker.gate.model.manageddelivery.EnvironmentArtifactVeto;
import com.netflix.spinnaker.gate.model.manageddelivery.Resource;
import java.util.List;
import java.util.Map;
import retrofit.client.Response;
import retrofit.http.Body;
import retrofit.http.DELETE;
import retrofit.http.GET;
import retrofit.http.Headers;
import retrofit.http.POST;
import retrofit.http.Path;
import retrofit.http.Query;

public interface KeelService {

  @GET("/resources/events/{name}")
  List<Map<String, Object>> getResourceEvents(
      @Path("name") String name, @Query("limit") Integer limit);

  @GET("/resources/{name}")
  Resource getResource(@Path("name") String name);

  @GET("/resources/{name}/status")
  String getResourceStatus(@Path("name") String name);

  @POST("/resources")
  Resource upsertResource(@Body Resource resource);

  @POST("/resources/diff")
  Map diffResource(@Body Resource resource);

  @DELETE("/resources/{name}")
  Resource deleteResource(@Path("name") String name);

  @GET("/delivery-configs/{name}")
  DeliveryConfig getManifest(@Path("name") String name);

  @GET("/delivery-configs/{name}/artifacts")
  List<Map<String, Object>> getManifestArtifacts(@Path("name") String name);

  @POST("/delivery-configs")
  @Headers("Accept: application/json")
  DeliveryConfig upsertManifest(@Body DeliveryConfig manifest);

  @DELETE("/delivery-configs/{name}")
  DeliveryConfig deleteManifest(@Path("name") String name);

  @POST("/delivery-configs/diff")
  List<Map> diffManifest(@Body DeliveryConfig manifest);

  @POST("/delivery-configs/validate")
  @Headers("Accept: application/json")
  Map validateManifest(@Body DeliveryConfig manifest);

  @GET("/application/{application}/environment/{environment}/constraints")
  List<ConstraintState> getConstraintState(
      @Path("application") String application,
      @Path("environment") String environment,
      @Query("limit") Integer limit);

  @POST("/application/{application}/environment/{environment}/constraint")
  Response updateConstraintStatus(
      @Path("application") String application,
      @Path("environment") String environment,
      @Body ConstraintStatus status);

  @GET("/application/{application}")
  Map getApplicationDetails(
      @Path("application") String application,
      @Query("includeDetails") Boolean includeDetails,
      @Query("entities") List<String> entities);

  @POST("/application/{application}/pause")
  Response pauseApplication(@Path("application") String application, @Body Map requestBody);

  @DELETE("/application/{application}/pause")
  Response resumeApplication(@Path("application") String application);

  @POST("/resources/{name}/pause")
  Response pauseResource(@Path("name") String name, @Body Map requestBody);

  @DELETE("/resources/{name}/pause")
  Response resumeResource(@Path("name") String name);

  @GET("/export/{cloudProvider}/{account}/{type}/{name}")
  Resource exportResource(
      @Path("cloudProvider") String cloudProvider,
      @Path("account") String account,
      @Path("type") String type,
      @Path("name") String name,
      @Query("serviceAccount") String serviceAccount);

  @POST("/application/{application}/pin")
  Response pin(@Path("application") String application, @Body EnvironmentArtifactPin pin);

  @DELETE("/application/{application}/pin")
  Response deletePin(@Path("application") String application, @Body EnvironmentArtifactPin pin);

  @DELETE("/application/{application}/pin/{targetEnvironment}")
  Response deletePinForEnvironment(
      @Path("application") String application, @Path("targetEnvironment") String targetEnvironment);

  @POST("/application/{application}/veto")
  Response veto(@Path("application") String application, @Body EnvironmentArtifactVeto veto);

  @DELETE("/application/{application}/veto/{targetEnvironment}/{reference}/{version}")
  Response deleteVeto(
      @Path("application") String application,
      @Path("targetEnvironment") String targetEnvironment,
      @Path("reference") String reference,
      @Path("version") String version);
}
