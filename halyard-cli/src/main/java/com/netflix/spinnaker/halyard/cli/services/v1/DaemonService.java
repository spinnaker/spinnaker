/*
 * Copyright 2016 Google, Inc.
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
 */

package com.netflix.spinnaker.halyard.cli.services.v1;

import com.netflix.spinnaker.halyard.config.model.v1.node.*;

import java.util.List;

import com.netflix.spinnaker.halyard.core.DaemonResponse;
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTask;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.Versions;
import retrofit.http.Body;
import retrofit.http.DELETE;
import retrofit.http.GET;
import retrofit.http.POST;
import retrofit.http.PUT;
import retrofit.http.Path;
import retrofit.http.Query;

public interface DaemonService {
  @GET("/v1/tasks/{uuid}/")
  <T> DaemonTask<T> getTask(@Path("uuid") String uuid);

  @GET("/v1/config/")
  DaemonResponse<Halconfig> getHalconfig();

  @GET("/v1/config/currentDeployment/")
  DaemonResponse<String> getCurrentDeployment();

  @GET("/v1/config/deployments/")
  DaemonResponse<List<DeploymentConfiguration>> getDeployments();

  @GET("/v1/config/deployments/{deploymentName}/")
  DaemonResponse<DeploymentConfiguration> getDeployment(
      @Path("deploymentName") String deploymentName,
      @Query("validate") boolean validate);

  @POST("/v1/config/deployments/{deploymentName}/generate/")
  DaemonTask<Void> generateDeployment(
      @Path("deploymentName") String deploymentName,
      @Query("validate") boolean validate,
      @Body String _ignore);

  @POST("/v1/config/deployments/{deploymentName}/deploy/")
  DaemonTask<Void> deployDeployment(
      @Path("deploymentName") String deploymentName,
      @Query("validate") boolean validate,
      @Body String _ignore);

  @GET("/v1/config/deployments/{deploymentName}/features/")
  DaemonResponse<Features> getFeatures(
      @Path("deploymentName") String deploymentName,
      @Query("validate") boolean validate);


  @PUT("/v1/config/deployments/{deploymentName}/features/")
  DaemonResponse<Void> setFeatures(
      @Path("deploymentName") String deploymentName,
      @Query("validate") boolean validate,
      @Body Features features);

  @GET("/v1/config/deployments/{deploymentName}/persistentStorage/")
  DaemonResponse<PersistentStorage> getPersistentStorage(
      @Path("deploymentName") String deploymentName,
      @Query("validate") boolean validate);


  @PUT("/v1/config/deployments/{deploymentName}/persistentStorage/")
  DaemonResponse<Void> setPersistentStorage(
      @Path("deploymentName") String deploymentName,
      @Query("validate") boolean validate,
      @Body PersistentStorage persistentStorage);

  @GET("/v1/config/deployments/{deploymentName}/providers/{providerName}/")
  DaemonResponse<Object> getProvider(
      @Path("deploymentName") String deploymentName,
      @Path("providerName") String providerName,
      @Query("validate") boolean validate);

  @PUT("/v1/config/deployments/{deploymentName}/providers/{providerName}/enabled/")
  DaemonResponse<Void> setProviderEnabled(
      @Path("deploymentName") String deploymentName,
      @Path("providerName") String providerName,
      @Query("validate") boolean validate,
      @Body boolean enabled);

  @POST("/v1/config/deployments/{deploymentName}/providers/{providerName}/accounts/")
  DaemonResponse<Void> addAccount(
      @Path("deploymentName") String deploymentName,
      @Path("providerName") String providerName,
      @Query("validate") boolean validate,
      @Body Account account);

  @GET("/v1/config/deployments/{deploymentName}/providers/{providerName}/accounts/{accountName}/")
  DaemonResponse<Object> getAccount(
      @Path("deploymentName") String deploymentName,
      @Path("providerName") String providerName,
      @Path("accountName") String accountName,
      @Query("validate") boolean validate);

  @PUT("/v1/config/deployments/{deploymentName}/providers/{providerName}/accounts/{accountName}/")
  DaemonResponse<Void> setAccount(
      @Path("deploymentName") String deploymentName,
      @Path("providerName") String providerName,
      @Path("accountName") String accountName,
      @Query("validate") boolean validate,
      @Body Account account);

  @DELETE("/v1/config/deployments/{deploymentName}/providers/{providerName}/accounts/{accountName}/")
  DaemonResponse<Object> deleteAccount(
      @Path("deploymentName") String deploymentName,
      @Path("providerName") String providerName,
      @Path("accountName") String accountName,
      @Query("validate") boolean validate);

  @GET("/v1/versions/")
  DaemonResponse<Versions> getVersions();
}
