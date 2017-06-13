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
import com.netflix.spinnaker.halyard.config.model.v1.security.*;
import com.netflix.spinnaker.halyard.core.DaemonOptions;
import com.netflix.spinnaker.halyard.core.StringBodyRequest;
import com.netflix.spinnaker.halyard.core.registry.v1.Versions;
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTask;
import com.netflix.spinnaker.halyard.core.tasks.v1.ShallowTaskList;
import com.netflix.spinnaker.halyard.deploy.deployment.v1.DeployOption;
import retrofit.client.Response;
import retrofit.http.*;

import java.util.List;
import java.util.Map;

public interface DaemonService {
  @GET("/health")
  Map<String, String> getHealth();

  @GET("/v1/tasks/")
  ShallowTaskList getTasks();

  @PUT("/v1/tasks/{uuid}/interrupt")
  Response interruptTask(@Path("uuid") String uuid, @Body String _ignore);

  @GET("/v1/tasks/{uuid}/")
  <C, T> DaemonTask<C, T> getTask(@Path("uuid") String uuid);

  @PUT("/v1/backup/create")
  DaemonTask<Halconfig, Object> createBackup(@Body String _ignore);

  @PUT("/v1/backup/restore")
  DaemonTask<Halconfig, Void> restoreBackup(
      @Query("backupPath") String backupPath,
      @Body String _ignore);

  @GET("/v1/config/")
  DaemonTask<Halconfig, Halconfig> getHalconfig();

  @GET("/v1/config/currentDeployment/")
  DaemonTask<Halconfig, String> getCurrentDeployment();

  @PUT("/v1/config/currentDeployment/")
  DaemonTask<Halconfig, Void> setCurrentDeployment(@Body StringBodyRequest name);

  @GET("/v1/config/deployments/")
  DaemonTask<Halconfig, List<DeploymentConfiguration>> getDeployments();

  @GET("/v1/config/deployments/{deploymentName}/")
  DaemonTask<Halconfig, Object> getDeployment(
      @Path("deploymentName") String deploymentName,
      @Query("validate") boolean validate);

  @PUT("/v1/config/deployments/{deploymentName}/")
  DaemonTask<Halconfig, Void> setDeployment(
      @Path("deploymentName") String deploymentName,
      @Query("validate") boolean validate,
      @Body DeploymentConfiguration deploymentConfiguration);

  @POST("/v1/config/deployments/{deploymentName}/generate/")
  DaemonTask<Halconfig, Void> generateDeployment(
      @Path("deploymentName") String deploymentName,
      @Query("validate") boolean validate,
      @Body String _ignore);

  @POST("/v1/config/deployments/{deploymentName}/connect/")
  DaemonTask<Halconfig, Object> connectToDeployment(
      @Path("deploymentName") String deploymentName,
      @Query("validate") boolean validate,
      @Query("serviceNames") List<String> serviceNames,
      @Body String _ignore);

  @POST("/v1/config/deployments/{deploymentName}/deploy/")
  DaemonTask<Halconfig, Object> deployDeployment(
      @Path("deploymentName") String deploymentName,
      @Query("validate") boolean validate,
      @Query("deployOptions") List<DeployOption> deployOptions,
      @Query("serviceNames") List<String> serviceNames,
      @Body String _ignore);

  @POST("/v1/config/deployments/{deploymentName}/rollback/")
  DaemonTask<Halconfig, Object> rollbackDeployment(
      @Path("deploymentName") String deploymentName,
      @Query("validate") boolean validate,
      @Query("serviceNames") List<String> serviceNames,
      @Body String _ignore);

  @PUT("/v1/config/deployments/{deploymentName}/collectLogs/")
  DaemonTask<Halconfig, Object> collectLogs(
      @Path("deploymentName") String deploymentName,
      @Query("validate") boolean validate,
      @Query("serviceNames") List<String> serviceNames,
      @Body String _ignore);

  @POST("/v1/config/deployments/{deploymentName}/clean/")
  DaemonTask<Halconfig, Object> cleanDeployment(
      @Path("deploymentName") String deploymentName,
      @Query("validate") boolean validate,
      @Body String _ignore);

  @GET("/v1/config/deployments/{deploymentName}/configDiff/")
  DaemonTask<Halconfig, Object> configDiff(
      @Path("deploymentName") String deploymentName,
      @Query("validate") boolean validate);

  @GET("/v1/config/deployments/{deploymentName}/deploymentEnvironment/")
  DaemonTask<Halconfig, Object> getDeploymentEnvironment(
      @Path("deploymentName") String deploymentName,
      @Query("validate") boolean validate);

  @PUT("/v1/config/deployments/{deploymentName}/deploymentEnvironment/")
  DaemonTask<Halconfig, Void> setDeploymentEnvironment(
      @Path("deploymentName") String deploymentName,
      @Query("validate") boolean validate,
      @Body DeploymentEnvironment deploymentEnvironment);

  @GET("/v1/config/deployments/{deploymentName}/features/")
  DaemonTask<Halconfig, Object> getFeatures(
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

  @PUT("/v1/config/deployments/{deploymentName}/persistentStorage/{persistentStoreType}/")
  DaemonTask<Halconfig, Void> setPersistentStore(
      @Path("deploymentName") String deploymentName,
      @Path("persistentStoreType") String persistentStoreType,
      @Query("validate") boolean validate,
      @Body PersistentStore persistentStore);

  @GET("/v1/config/deployments/{deploymentName}/providers/{providerName}/")
  DaemonTask<Halconfig, Object> getProvider(
      @Path("deploymentName") String deploymentName,
      @Path("providerName") String providerName,
      @Query("validate") boolean validate);

  @PUT("/v1/config/deployments/{deploymentName}/providers/{providerName}/")
  DaemonTask<Halconfig, Object> setProvider(
      @Path("deploymentName") String deploymentName,
      @Path("providerName") String providerName,
      @Query("validate") boolean validate,
      @Body Provider provider);

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

  @GET("/v1/config/deployments/{deploymentName}/providers/{providerName}/accounts/account/{accountName}/")
  DaemonTask<Halconfig, Object> getAccount(
      @Path("deploymentName") String deploymentName,
      @Path("providerName") String providerName,
      @Path("accountName") String accountName,
      @Query("validate") boolean validate);

  @PUT("/v1/config/deployments/{deploymentName}/providers/{providerName}/accounts/account/{accountName}/")
  DaemonTask<Halconfig, Void> setAccount(
      @Path("deploymentName") String deploymentName,
      @Path("providerName") String providerName,
      @Path("accountName") String accountName,
      @Query("validate") boolean validate,
      @Body Account account);

  @DELETE("/v1/config/deployments/{deploymentName}/providers/{providerName}/accounts/account/{accountName}/")
  DaemonTask<Halconfig, Void> deleteAccount(
      @Path("deploymentName") String deploymentName,
      @Path("providerName") String providerName,
      @Path("accountName") String accountName,
      @Query("validate") boolean validate);

  @POST("/v1/config/deployments/{deploymentName}/providers/{providerName}/accounts/options")
  DaemonTask<Halconfig, List<String>> getNewAccountOptions(
      @Path("deploymentName") String deploymentName,
      @Path("providerName") String providerName,
      @Body DaemonOptions<Account> options);

  @PUT("/v1/config/deployments/{deploymentName}/providers/{providerName}/accounts/account/{accountName}/options")
  DaemonTask<Halconfig, List<String>> getExistingAccountOptions(
      @Path("deploymentName") String deploymentName,
      @Path("providerName") String providerName,
      @Path("accountName") String accountName,
      @Body DaemonOptions<Void> options);

  @POST("/v1/config/deployments/{deploymentName}/providers/{providerName}/clusters/")
  DaemonTask<Halconfig, Void> addCluster(
      @Path("deploymentName") String deploymentName,
      @Path("providerName") String providerName,
      @Query("validate") boolean validate,
      @Body Cluster cluster);

  @GET("/v1/config/deployments/{deploymentName}/providers/{providerName}/clusters/cluster/{clusterName}/")
  DaemonTask<Halconfig, Object> getCluster(
      @Path("deploymentName") String deploymentName,
      @Path("providerName") String providerName,
      @Path("clusterName") String clusterName,
      @Query("validate") boolean validate);

  @PUT("/v1/config/deployments/{deploymentName}/providers/{providerName}/clusters/cluster/{clusterName}/")
  DaemonTask<Halconfig, Void> setCluster(
      @Path("deploymentName") String deploymentName,
      @Path("providerName") String providerName,
      @Path("clusterName") String clusterName,
      @Query("validate") boolean validate,
      @Body Cluster cluster);

  @DELETE("/v1/config/deployments/{deploymentName}/providers/{providerName}/clusters/cluster/{clusterName}/")
  DaemonTask<Halconfig, Void> deleteCluster(
      @Path("deploymentName") String deploymentName,
      @Path("providerName") String providerName,
      @Path("clusterName") String clusterName,
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

  @GET("/v1/config/deployments/{deploymentName}/metricStores/")
  DaemonTask<Halconfig, Object> getMetricStores(
      @Path("deploymentName") String deploymentName,
      @Query("validate") boolean validate);

  @PUT("/v1/config/deployments/{deploymentName}/metricStores/")
  DaemonTask<Halconfig, Void> setMetricStores(
      @Path("deploymentName") String deploymentName,
      @Query("validate") boolean validate,
      @Body MetricStores metricStores);

  @GET("/v1/config/deployments/{deploymentName}/metricStores/{metricStoreType}/")
  DaemonTask<Halconfig, Object> getMetricStore(
      @Path("deploymentName") String deploymentName,
      @Path("metricStoreType") String metricStoreType,
      @Query("validate") boolean validate);

  @PUT("/v1/config/deployments/{deploymentName}/metricStores/{metricStoreType}/")
  DaemonTask<Halconfig, Void> setMetricStore(
      @Path("deploymentName") String deploymentName,
      @Path("metricStoreType") String metricStoreType,
      @Query("validate") boolean validate,
      @Body Map metricStore);

  @PUT("/v1/config/deployments/{deploymentName}/metricStores/{metricStoreType}/enabled/")
  DaemonTask<Halconfig, Void> setMetricStoreEnabled(
      @Path("deploymentName") String deploymentName,
      @Path("metricStoreType") String metricStoreType,
      @Query("validate") boolean validate,
      @Body boolean enabled);

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
  DaemonTask<Halconfig, Object> getServiceDetails(
      @Path("deploymentName") String deploymentName,
      @Path("serviceName") String serviceName,
      @Query("validate") boolean validate);

  @GET("/v1/config/deployments/{deploymentName}/security/api/")
  DaemonTask<Halconfig, Object> getApiSecurity(
      @Path("deploymentName") String deploymentName,
      @Query("validate") boolean validate);

  @PUT("/v1/config/deployments/{deploymentName}/security/api/")
  DaemonTask<Halconfig, Void> setApiSecurity(
      @Path("deploymentName") String deploymentName,
      @Query("validate") boolean validate,
      @Body ApiSecurity apiSecurity);

  @GET("/v1/config/deployments/{deploymentName}/security/api/ssl/")
  DaemonTask<Halconfig, Object> getSpringSsl(
      @Path("deploymentName") String deploymentName,
      @Query("validate") boolean validate);

  @PUT("/v1/config/deployments/{deploymentName}/security/api/ssl/")
  DaemonTask<Halconfig, Void> setSpringSsl(
      @Path("deploymentName") String deploymentName,
      @Query("validate") boolean validate,
      @Body SpringSsl springSsl);

  @PUT("/v1/config/deployments/{deploymentName}/security/api/ssl/enabled/")
  DaemonTask<Halconfig, Void> setSpringSslEnabled(
      @Path("deploymentName") String deploymentName,
      @Query("validate") boolean validate,
      @Body boolean enabled);

  @GET("/v1/config/deployments/{deploymentName}/security/ui/")
  DaemonTask<Halconfig, Object> getUiSecurity(
      @Path("deploymentName") String deploymentName,
      @Query("validate") boolean validate);

  @PUT("/v1/config/deployments/{deploymentName}/security/ui/")
  DaemonTask<Halconfig, Void> setUiSecurity(
      @Path("deploymentName") String deploymentName,
      @Query("validate") boolean validate,
      @Body UiSecurity uiSecurity);

  @GET("/v1/config/deployments/{deploymentName}/security/ui/ssl/")
  DaemonTask<Halconfig, Object> getApacheSsl(
      @Path("deploymentName") String deploymentName,
      @Query("validate") boolean validate);

  @PUT("/v1/config/deployments/{deploymentName}/security/ui/ssl/")
  DaemonTask<Halconfig, Void> setApacheSsl(
      @Path("deploymentName") String deploymentName,
      @Query("validate") boolean validate,
      @Body ApacheSsl apacheSsl);

  @PUT("/v1/config/deployments/{deploymentName}/security/ui/ssl/enabled/")
  DaemonTask<Halconfig, Void> setApacheSslEnabled(
      @Path("deploymentName") String deploymentName,
      @Query("validate") boolean validate,
      @Body boolean enabled);

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

  @GET("/v1/config/deployments/{deploymentName}/persistentStorage/{persistentStoreType}/")
  DaemonTask<Halconfig, Object> getPersistentStore(
      @Path("deploymentName") String deploymentName,
      @Path("persistentStoreType") String persistentStoreType,
      @Query("validate") boolean validate);

  @GET("/v1/config/deployments/{deploymentName}/persistentStorage/")
  DaemonTask<Halconfig, Object> getPersistentStorage(
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

  @GET("/v1/config/deployments/{deploymentName}/ci/{ciName}/")
  DaemonTask<Halconfig, Object> getCi(
      @Path("deploymentName") String deploymentName,
      @Path("ciName") String ciName,
      @Query("validate") boolean validate);

  @PUT("/v1/config/deployments/{deploymentName}/ci/{ciName}/enabled/")
  DaemonTask<Halconfig, Void> setCiEnabled(
      @Path("deploymentName") String deploymentName,
      @Path("ciName") String ciName,
      @Query("validate") boolean validate,
      @Body boolean enabled);

  @POST("/v1/config/deployments/{deploymentName}/ci/{ciName}/masters/")
  DaemonTask<Halconfig, Void> addMaster(
      @Path("deploymentName") String deploymentName,
      @Path("ciName") String ciName,
      @Query("validate") boolean validate,
      @Body Master master);

  @GET("/v1/config/deployments/{deploymentName}/ci/{ciName}/masters/{masterName}/")
  DaemonTask<Halconfig, Object> getMaster(
      @Path("deploymentName") String deploymentName,
      @Path("ciName") String ciName,
      @Path("masterName") String masterName,
      @Query("validate") boolean validate);

  @PUT("/v1/config/deployments/{deploymentName}/ci/{ciName}/masters/{masterName}/")
  DaemonTask<Halconfig, Void> setMaster(
      @Path("deploymentName") String deploymentName,
      @Path("ciName") String ciName,
      @Path("masterName") String masterName,
      @Query("validate") boolean validate,
      @Body Master master);

  @DELETE("/v1/config/deployments/{deploymentName}/ci/{ciName}/masters/{masterName}/")
  DaemonTask<Halconfig, Void> deleteMaster(
      @Path("deploymentName") String deploymentName,
      @Path("ciName") String ciName,
      @Path("masterName") String masterName,
      @Query("validate") boolean validate);

  @GET("/v1/versions/")
  DaemonTask<Halconfig, Versions> getVersions();

  @GET("/v1/versions/latest/")
  DaemonTask<Halconfig, String> getLatest();

  @GET("/v1/versions/bom/{version}")
  DaemonTask<Halconfig, Object> getBillOfMaterials(@Path("version") String version);

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

  @PUT("/v1/admin/deprecateVersion")
  DaemonTask<Halconfig, Void> deprecateVersion(
      @Body Versions.Version version,
      @Query("illegalReason") String illegalReason);

  @PUT("/v1/admin/publishVersion")
  DaemonTask<Halconfig, Void> publishVersion(
      @Body Versions.Version version);

  @PUT("/v1/admin/publishLatest")
  DaemonTask<Halconfig, Void> publishLatestHalyard(
      @Query("latestHalyard") String latestHalyard,
      @Body String _ignore);

  @PUT("/v1/admin/publishLatest")
  DaemonTask<Halconfig, Void> publishLatestSpinnaker(
      @Query("latestSpinnaker") String latestSpinnaker,
      @Body String _ignore);
}
