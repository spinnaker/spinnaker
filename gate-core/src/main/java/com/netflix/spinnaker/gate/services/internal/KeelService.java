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
import com.netflix.spinnaker.gate.model.manageddelivery.OverrideVerificationRequest;
import com.netflix.spinnaker.gate.model.manageddelivery.Resource;
import com.netflix.spinnaker.gate.model.manageddelivery.RetryVerificationRequest;
import com.netflix.spinnaker.kork.plugins.SpinnakerPluginDescriptor;
import java.util.List;
import java.util.Map;
import retrofit.client.Response;
import retrofit.http.Body;
import retrofit.http.DELETE;
import retrofit.http.GET;
import retrofit.http.Header;
import retrofit.http.Headers;
import retrofit.http.POST;
import retrofit.http.Path;
import retrofit.http.Query;
import retrofit.http.QueryMap;

public interface KeelService {

  @GET("/resources/events/{name}")
  List<Map<String, Object>> getResourceEvents(
      @Path("name") String name, @Query("limit") Integer limit);

  @GET("/resources/{name}")
  Resource getResource(@Path("name") String name);

  @GET("/resources/{name}")
  @Headers("Accept: application/x-yaml")
  Resource getResourceYaml(@Path("name") String name);

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

  @GET("/delivery-configs/{name}")
  @Headers("Accept: application/x-yaml")
  DeliveryConfig getManifestYaml(@Path("name") String name);

  @GET("/delivery-configs/{name}/artifacts")
  List<Map<String, Object>> getManifestArtifacts(@Path("name") String name);

  @POST("/delivery-configs")
  @Headers("Accept: application/json")
  DeliveryConfig upsertManifest(@Body DeliveryConfig manifest);

  @DELETE("/delivery-configs/{name}")
  DeliveryConfig deleteManifest(@Path("name") String name);

  @DELETE("/application/{application}/config")
  DeliveryConfig deleteManifestByAppName(@Path("application") String application);

  @POST("/delivery-configs/diff")
  List<Map> diffManifest(@Body DeliveryConfig manifest);

  @GET("/delivery-configs/schema")
  Map<String, Object> schema();

  @POST("/delivery-configs/validate")
  @Headers("Accept: application/json")
  Map validateManifest(@Body DeliveryConfig manifest);

  @GET("/application/{application}/config")
  DeliveryConfig getConfigBy(@Path("application") String application);

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
      @Query("entities") List<String> entities,
      @Query("maxArtifactVersions") Integer maxArtifactVersions);

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

  @GET("/export/artifact/{cloudProvider}/{account}/{clusterName}")
  Map<String, Object> exportArtifact(
      @Path("cloudProvider") String cloudProvider,
      @Path("account") String account,
      @Path("clusterName") String clusterName);

  @POST("/application/{application}/pin")
  Response pin(@Path("application") String application, @Body EnvironmentArtifactPin pin);

  @DELETE("/application/{application}/pin/{targetEnvironment}")
  Response deletePinForEnvironment(
      @Path("application") String application,
      @Path("targetEnvironment") String targetEnvironment,
      @Query("reference") String reference);

  @POST("/application/{application}/veto")
  Response veto(@Path("application") String application, @Body EnvironmentArtifactVeto veto);

  @POST("/application/{application}/mark/bad")
  Response markBad(@Path("application") String application, @Body EnvironmentArtifactVeto veto);

  @DELETE("/application/{application}/veto/{targetEnvironment}/{reference}/{version}")
  Response deleteVeto(
      @Path("application") String application,
      @Path("targetEnvironment") String targetEnvironment,
      @Path("reference") String reference,
      @Path("version") String version);

  @POST("/application/{application}/mark/good")
  Response markGood(@Path("application") String application, @Body EnvironmentArtifactVeto veto);

  @POST("/application/{application}/environment/{environment}/verifications")
  Response overrideVerification(
      @Path("application") String application,
      @Path("environment") String environment,
      @Body OverrideVerificationRequest payload);

  @POST("/application/{application}/environment/{environment}/verifications/retry")
  Response retryVerification(
      @Path("application") String application,
      @Path("environment") String environment,
      @Body RetryVerificationRequest payload);

  @GET("/installedPlugins")
  List<SpinnakerPluginDescriptor> getInstalledPlugins();

  @GET("/reports/onboarding")
  Response getOnboardingReport(
      @Header("Accept") String accept, @QueryMap Map<String, String> params);

  @GET("/reports/adoption")
  @Headers("Accept: text/html")
  Response getAdoptionReport(@QueryMap Map<String, String> params);

  @GET("/environments/{application}")
  List<Map<String, Object>> getEnvironments(@Path("application") String application);
}
