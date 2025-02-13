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
import com.netflix.spinnaker.gate.model.manageddelivery.GraphQLRequest;
import com.netflix.spinnaker.gate.model.manageddelivery.OverrideVerificationRequest;
import com.netflix.spinnaker.gate.model.manageddelivery.Resource;
import com.netflix.spinnaker.gate.model.manageddelivery.RetryVerificationRequest;
import com.netflix.spinnaker.kork.plugins.SpinnakerPluginDescriptor;
import java.util.List;
import java.util.Map;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Headers;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;
import retrofit2.http.QueryMap;

public interface KeelService {

  @POST("/graphql")
  @Headers("Accept: application/json")
  Call<Map<String, Object>> graphql(@Body GraphQLRequest query);

  @GET("/resources/events/{name}")
  Call<List<Map<String, Object>>> getResourceEvents(
      @Path("name") String name, @Query("limit") Integer limit);

  @GET("/resources/{name}")
  Call<Resource> getResource(@Path("name") String name);

  @GET("/resources/{name}")
  @Headers("Accept: application/x-yaml")
  Call<Resource> getResourceYaml(@Path("name") String name);

  @GET("/resources/{name}/status")
  Call<String> getResourceStatus(@Path("name") String name);

  @POST("/resources")
  Call<Resource> upsertResource(@Body Resource resource);

  @POST("/resources/diff")
  Call<Map> diffResource(@Body Resource resource);

  @DELETE("/resources/{name}")
  Call<Resource> deleteResource(@Path("name") String name);

  @GET("/delivery-configs/{name}")
  Call<DeliveryConfig> getManifest(@Path("name") String name);

  @GET("/delivery-configs/{name}")
  @Headers("Accept: application/x-yaml")
  Call<DeliveryConfig> getManifestYaml(@Path("name") String name);

  @GET("/delivery-configs/{name}/artifacts")
  Call<List<Map<String, Object>>> getManifestArtifacts(@Path("name") String name);

  @POST("/delivery-configs")
  @Headers("Accept: application/json")
  Call<DeliveryConfig> upsertManifest(@Body DeliveryConfig manifest);

  @DELETE("/delivery-configs/{name}")
  Call<DeliveryConfig> deleteManifest(@Path("name") String name);

  @DELETE("/application/{application}/config")
  Call<DeliveryConfig> deleteManifestByAppName(@Path("application") String application);

  @POST("/delivery-configs/diff")
  Call<List<Map>> diffManifest(@Body DeliveryConfig manifest);

  @GET("/delivery-configs/schema")
  Call<Map<String, Object>> schema();

  @POST("/delivery-configs/validate")
  @Headers("Accept: application/json")
  Call<Map> validateManifest(@Body DeliveryConfig manifest);

  @GET("/application/{application}/config")
  Call<DeliveryConfig> getConfigBy(@Path("application") String application);

  @GET("/application/{application}/environment/{environment}/constraints")
  Call<List<ConstraintState>> getConstraintState(
      @Path("application") String application,
      @Path("environment") String environment,
      @Query("limit") Integer limit);

  @POST("/application/{application}/environment/{environment}/constraint")
  Call<ResponseBody> updateConstraintStatus(
      @Path("application") String application,
      @Path("environment") String environment,
      @Body ConstraintStatus status);

  @GET("/application/{application}")
  Call<Map> getApplicationDetails(
      @Path("application") String application,
      @Query("includeDetails") Boolean includeDetails,
      @Query("entities") List<String> entities,
      @Query("maxArtifactVersions") Integer maxArtifactVersions);

  @POST("/application/{application}/pause")
  Call<ResponseBody> pauseApplication(
      @Path("application") String application, @Body Map requestBody);

  @DELETE("/application/{application}/pause")
  Call<ResponseBody> resumeApplication(@Path("application") String application);

  @POST("/resources/{name}/pause")
  Call<ResponseBody> pauseResource(@Path("name") String name, @Body Map requestBody);

  @DELETE("/resources/{name}/pause")
  Call<ResponseBody> resumeResource(@Path("name") String name);

  @GET("/export/{cloudProvider}/{account}/{type}/{name}")
  Call<Resource> exportResource(
      @Path("cloudProvider") String cloudProvider,
      @Path("account") String account,
      @Path("type") String type,
      @Path("name") String name,
      @Query("serviceAccount") String serviceAccount);

  @GET("/export/artifact/{cloudProvider}/{account}/{clusterName}")
  Call<Map<String, Object>> exportArtifact(
      @Path("cloudProvider") String cloudProvider,
      @Path("account") String account,
      @Path("clusterName") String clusterName);

  @POST("/application/{application}/pin")
  Call<ResponseBody> pin(@Path("application") String application, @Body EnvironmentArtifactPin pin);

  @DELETE("/application/{application}/pin/{targetEnvironment}")
  Call<ResponseBody> deletePinForEnvironment(
      @Path("application") String application,
      @Path("targetEnvironment") String targetEnvironment,
      @Query("reference") String reference);

  @POST("/application/{application}/veto")
  Call<ResponseBody> veto(
      @Path("application") String application, @Body EnvironmentArtifactVeto veto);

  @POST("/application/{application}/mark/bad")
  Call<ResponseBody> markBad(
      @Path("application") String application, @Body EnvironmentArtifactVeto veto);

  @DELETE("/application/{application}/veto/{targetEnvironment}/{reference}/{version}")
  Call<ResponseBody> deleteVeto(
      @Path("application") String application,
      @Path("targetEnvironment") String targetEnvironment,
      @Path("reference") String reference,
      @Path("version") String version);

  @POST("/application/{application}/mark/good")
  Call<ResponseBody> markGood(
      @Path("application") String application, @Body EnvironmentArtifactVeto veto);

  @POST("/application/{application}/environment/{environment}/verifications")
  Call<ResponseBody> overrideVerification(
      @Path("application") String application,
      @Path("environment") String environment,
      @Body OverrideVerificationRequest payload);

  @POST("/application/{application}/environment/{environment}/verifications/retry")
  Call<ResponseBody> retryVerification(
      @Path("application") String application,
      @Path("environment") String environment,
      @Body RetryVerificationRequest payload);

  @GET("/installedPlugins")
  Call<List<SpinnakerPluginDescriptor>> getInstalledPlugins();

  @GET("/reports/onboarding")
  Call<ResponseBody> getOnboardingReport(
      @Header("Accept") String accept, @QueryMap Map<String, String> params);

  @GET("/reports/adoption")
  @Headers("Accept: text/html")
  Call<ResponseBody> getAdoptionReport(@QueryMap Map<String, String> params);

  @GET("/environments/{application}")
  Call<List<Map<String, Object>>> getEnvironments(@Path("application") String application);
}
