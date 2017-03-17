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
import com.netflix.spinnaker.halyard.config.model.v1.security.AuthnMethod;
import com.netflix.spinnaker.halyard.config.model.v1.security.GroupMembership;
import com.netflix.spinnaker.halyard.config.model.v1.security.RoleProvider;
import com.netflix.spinnaker.halyard.config.model.v1.security.Security;
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTask;
import com.netflix.spinnaker.halyard.core.registry.v1.BillOfMaterials;
import com.netflix.spinnaker.halyard.core.registry.v1.Versions;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.RunningServiceDetails;
import retrofit.http.*;

import java.util.List;

public interface DaemonService {
  @GET("/v1/tasks/{uuid}/")
  <C, T> DaemonTask<C, T> getTask(@Path("uuid") String uuid);

  @GET("/v1/config/")
  DaemonTask<Halconfig, Halconfig> getHalconfig();

  @GET("/v1/config/currentDeployment/")
  DaemonTask<Halconfig, String> getCurrentDeployment();

  @GET("/v1/config/deployments/")
  DaemonTask<Halconfig, List<DeploymentConfiguration>> getDeployments();

  @GET("/v1/config/deployments/{deploymentName}/")
  DaemonTask<Halconfig, DeploymentConfiguration> getDeployment(
      @Path("deploymentName") String deploymentName,
      @Query("validate") boolean validate);

  @POST("/v1/config/deployments/{deploymentName}/generate/")
  DaemonTask<Halconfig, Void> generateDeployment(
      @Path("deploymentName") String deploymentName,
      @Query("validate") boolean validate,
      @Body String _ignore);

  @POST("/v1/config/deployments/{deploymentName}/deploy/")
  DaemonTask<Halconfig, Object> deployDeployment(
      @Path("deploymentName") String deploymentName,
      @Query("validate") boolean validate,
      @Body String _ignore);

  @GET("/v1/config/deployments/{deploymentName}/configDiff/")
  DaemonTask<Halconfig, NodeDiff> configDiff(
      @Path("deploymentName") String deploymentName,
      @Query("validate") boolean validate);

  @GET("/v1/config/deployments/{deploymentName}/deploymentEnvironment/")
  DaemonTask<Halconfig, DeploymentEnvironment> getDeploymentEnvironment(
      @Path("deploymentName") String deploymentName,
      @Query("validate") boolean validate);

  @PUT("/v1/config/deployments/{deploymentName}/deploymentEnvironment/")
  DaemonTask<Halconfig, Void> setDeploymentEnvironment(
      @Path("deploymentName") String deploymentName,
      @Query("validate") boolean validate,
      @Body DeploymentEnvironment deploymentEnvironment);

  @GET("/v1/config/deployments/{deploymentName}/features/")
  DaemonTask<Halconfig, Features> getFeatures(
      @Path("deploymentName") String deploymentName,
      @Query("validate") boolean validate);

  @PUT("/v1/config/deployments/{deploymentName}/features/")
  DaemonTask<Halconfig, Void> setFeatures(
      @Path("deploymentName") String deploymentName,
      @Query("validate") boolean validate,
      @Body Features features);

  @PUT("/v1/config/deployments/{deploymentName}/persistentStorage/")
  DaemonTask<Halconfig, Void> setPersistentStorage(
      @Path("deploymentName") String deploymentName,
      @Query("validate") boolean validate,
      @Body PersistentStorage persistentStorage);

  @GET("/v1/config/deployments/{deploymentName}/providers/{providerName}/")
  DaemonTask<Halconfig, Object> getProvider(
      @Path("deploymentName") String deploymentName,
      @Path("providerName") String providerName,
      @Query("validate") boolean validate);

  @PUT("/v1/config/deployments/{deploymentName}/providers/{providerName}/enabled/")
  DaemonTask<Halconfig, Void> setProviderEnabled(
      @Path("deploymentName") String deploymentName,
      @Path("providerName") String providerName,
      @Query("validate") boolean validate,
      @Body boolean enabled);

  @POST("/v1/config/deployments/{deploymentName}/providers/{providerName}/accounts/")
  DaemonTask<Halconfig, Void> addAccount(
      @Path("deploymentName") String deploymentName,
      @Path("providerName") String providerName,
      @Query("validate") boolean validate,
      @Body Account account);

  @GET("/v1/config/deployments/{deploymentName}/providers/{providerName}/accounts/{accountName}/")
  DaemonTask<Halconfig, Object> getAccount(
      @Path("deploymentName") String deploymentName,
      @Path("providerName") String providerName,
      @Path("accountName") String accountName,
      @Query("validate") boolean validate);

  @PUT("/v1/config/deployments/{deploymentName}/providers/{providerName}/accounts/{accountName}/")
  DaemonTask<Halconfig, Void> setAccount(
      @Path("deploymentName") String deploymentName,
      @Path("providerName") String providerName,
      @Path("accountName") String accountName,
      @Query("validate") boolean validate,
      @Body Account account);

  @DELETE("/v1/config/deployments/{deploymentName}/providers/{providerName}/accounts/{accountName}/")
  DaemonTask<Halconfig, Void> deleteAccount(
      @Path("deploymentName") String deploymentName,
      @Path("providerName") String providerName,
      @Path("accountName") String accountName,
      @Query("validate") boolean validate);

  @GET("/v1/config/deployments/{deploymentName}/security/")
  DaemonTask<Halconfig, Security> getSecurity(
      @Path("deploymentName") String deploymentName,
      @Query("validate") boolean validate);

  @PUT("/v1/config/deployments/{deploymentName}/security/")
  DaemonTask<Halconfig, Void> setSecurity(
      @Path("deploymentName") String deploymentName,
      @Query("validate") boolean validate,
      @Body Security security);

  @GET("/v1/config/deployments/{deploymentName}/version/")
  DaemonTask<Halconfig, String> getVersion(
      @Path("deploymentName") String deploymentName,
      @Query("validate") boolean validate);

  @PUT("/v1/config/deployments/{deploymentName}/version/")
  DaemonTask<Halconfig, Void> setVersion(
      @Path("deploymentName") String deploymentName,
      @Query("validate") boolean validate,
      @Body Versions.Version version);

  @GET("/v1/config/deployments/{deploymentName}/details/{serviceName}/")
  DaemonTask<Halconfig, RunningServiceDetails> getServiceDetails(
      @Path("deploymentName") String deploymentName,
      @Path("serviceName") String serviceName,
      @Query("validate") boolean validate);

  @GET("/v1/config/deployments/{deploymentName}/security/authn/{methodName}/")
  DaemonTask<Halconfig, Object> getAuthnMethod(
      @Path("deploymentName") String deploymentName,
      @Path("methodName") String methodName,
      @Query("validate") boolean validate);

  @PUT("/v1/config/deployments/{deploymentName}/security/authn/{methodName}/")
  DaemonTask<Halconfig, Void> setAuthnMethod(
      @Path("deploymentName") String deploymentName,
      @Path("methodName") String methodName,
      @Query("validate") boolean validate,
      @Body AuthnMethod authnMethod);

  @PUT("/v1/config/deployments/{deploymentName}/security/authn/{methodName}/enabled/")
  DaemonTask<Halconfig, Void> setAuthnMethodEnabled(
      @Path("deploymentName") String deploymentName,
      @Path("methodName") String methodName,
      @Query("validate") boolean validate,
      @Body boolean enabled);

  @PUT("/v1/config/deployments/{deploymentName}/security/authz/groupMembership")
  DaemonTask<Halconfig, Void> setGroupMembership(
      @Path("deploymentName") String deploymentName,
      @Query("validate") boolean validate,
      @Body GroupMembership membership);

  @GET("/v1/config/deployments/{deploymentName}/security/authz/groupMembership")
  DaemonTask<Halconfig, Object> getGroupMembership(
      @Path("deploymentName") String deploymentName,
      @Query("validate") boolean validate);

  @GET("/v1/config/deployments/{deploymentName}/security/authz/groupMembership/{roleProviderName}/")
  DaemonTask<Halconfig, Object> getRoleProvider(
      @Path("deploymentName") String deploymentName,
      @Path("roleProviderName") String roleProviderName,
      @Query("validate") boolean validate);

  @PUT("/v1/config/deployments/{deploymentName}/security/authz/groupMembership/{roleProviderName}/")
  DaemonTask<Halconfig, Void> setRoleProvider(
      @Path("deploymentName") String deploymentName,
      @Path("roleProviderName") String roleProviderName,
      @Query("validate") boolean validate,
      @Body RoleProvider roleProvider);

  @PUT("/v1/config/deployments/{deploymentName}/security/authz/enabled/")
  DaemonTask<Halconfig, Void> setAuthzEnabled(
      @Path("deploymentName") String deploymentName,
      @Query("validate") boolean validate,
      @Body boolean enabled);

  @GET("/v1/config/deployments/{deploymentName}/persistentStorage/")
  DaemonTask<Halconfig, PersistentStorage> getPersistentStorage(
      @Path("deploymentName") String deploymentName,
      @Query("validate") boolean validate);

  @PUT("/v1/config/deployments/{deploymentName}/providers/{providerName}/bakery/defaults/")
  DaemonTask<Halconfig, Void> setBakeryDefaults(
      @Path("deploymentName") String deploymentName,
      @Path("providerName") String providerName,
      @Query("validate") boolean validate,
      @Body BakeryDefaults bakeryDefaults);

  @GET("/v1/config/deployments/{deploymentName}/providers/{providerName}/bakery/defaults/")
  DaemonTask<Halconfig, Object> getBakeryDefaults(
      @Path("deploymentName") String deploymentName,
      @Path("providerName") String providerName,
      @Query("validate") boolean validate);

  @POST("/v1/config/deployments/{deploymentName}/providers/{providerName}/bakery/defaults/baseImage/")
  DaemonTask<Halconfig, Void> addBaseImage(
      @Path("deploymentName") String deploymentName,
      @Path("providerName") String providerName,
      @Query("validate") boolean validate,
      @Body BaseImage baseImage);

  @GET("/v1/config/deployments/{deploymentName}/providers/{providerName}/bakery/defaults/baseImage/{baseImageId}/")
  DaemonTask<Halconfig, Object> getBaseImage(
      @Path("deploymentName") String deploymentName,
      @Path("providerName") String providerName,
      @Path("baseImageId") String baseImageId,
      @Query("validate") boolean validate);

  @PUT("/v1/config/deployments/{deploymentName}/providers/{providerName}/bakery/defaults/baseImage/{baseImageId}/")
  DaemonTask<Halconfig, Void> setBaseImage(
      @Path("deploymentName") String deploymentName,
      @Path("providerName") String providerName,
      @Path("baseImageId") String baseImageId,
      @Query("validate") boolean validate,
      @Body BaseImage baseImage);

  @DELETE("/v1/config/deployments/{deploymentName}/providers/{providerName}/bakery/defaults/baseImage/{baseImageId}/")
  DaemonTask<Halconfig, Void> deleteBaseImage(
      @Path("deploymentName") String deploymentName,
      @Path("providerName") String providerName,
      @Path("baseImageId") String baseImageId,
      @Query("validate") boolean validate);

  @GET("/v1/config/deployments/{deploymentName}/webhooks/{webhookName}/")
  DaemonTask<Halconfig, Object> getWebhook(
      @Path("deploymentName") String deploymentName,
      @Path("webhookName") String webhookName,
      @Query("validate") boolean validate);

  @PUT("/v1/config/deployments/{deploymentName}/webhooks/{webhookName}/enabled/")
  DaemonTask<Halconfig, Void> setWebhookEnabled(
      @Path("deploymentName") String deploymentName,
      @Path("webhookName") String webhookName,
      @Query("validate") boolean validate,
      @Body boolean enabled);

  @POST("/v1/config/deployments/{deploymentName}/webhooks/{webhookName}/masters/")
  DaemonTask<Halconfig, Void> addMaster(
      @Path("deploymentName") String deploymentName,
      @Path("webhookName") String webhookName,
      @Query("validate") boolean validate,
      @Body Master master);

  @GET("/v1/config/deployments/{deploymentName}/webhooks/{webhookName}/masters/{masterName}/")
  DaemonTask<Halconfig, Object> getMaster(
      @Path("deploymentName") String deploymentName,
      @Path("webhookName") String webhookName,
      @Path("masterName") String masterName,
      @Query("validate") boolean validate);

  @PUT("/v1/config/deployments/{deploymentName}/webhooks/{webhookName}/masters/{masterName}/")
  DaemonTask<Halconfig, Void> setMaster(
      @Path("deploymentName") String deploymentName,
      @Path("webhookName") String webhookName,
      @Path("masterName") String masterName,
      @Query("validate") boolean validate,
      @Body Master master);

  @DELETE("/v1/config/deployments/{deploymentName}/webhooks/{webhookName}/masters/{masterName}/")
  DaemonTask<Halconfig, Void> deleteMaster(
      @Path("deploymentName") String deploymentName,
      @Path("webhookName") String webhookName,
      @Path("masterName") String masterName,
      @Query("validate") boolean validate);

  @GET("/v1/versions/")
  DaemonTask<Halconfig, Versions> getVersions();

  @GET("/v1/versions/latest/")
  DaemonTask<Halconfig, String> getLatest();

  @GET("/v1/versions/bom/{version}")
  DaemonTask<Halconfig, BillOfMaterials> getBillOfMaterials(@Path("version") String version);

  @PUT("/v1/admin/publishProfile/{artifactName}")
  DaemonTask<Halconfig, Void> publishProfile(
      @Query("bomPath") String bomPath,
      @Path("artifactName") String artifactName,
      @Query("profilePath") String profilePath,
      @Body String _ignore);

  @PUT("/v1/admin/publishBom")
  DaemonTask<Halconfig, Void> publishBom(
      @Query("bomPath") String bomPath,
      @Body String _ignore);
}
