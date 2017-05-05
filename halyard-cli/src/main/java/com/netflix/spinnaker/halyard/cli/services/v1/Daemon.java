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

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.halyard.cli.command.v1.GlobalOptions;
import com.netflix.spinnaker.halyard.config.model.v1.node.*;
import com.netflix.spinnaker.halyard.config.model.v1.security.*;
import com.netflix.spinnaker.halyard.core.DaemonOptions;
import com.netflix.spinnaker.halyard.core.RemoteAction;
import com.netflix.spinnaker.halyard.core.registry.v1.BillOfMaterials;
import com.netflix.spinnaker.halyard.core.registry.v1.Versions;
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTask;
import com.netflix.spinnaker.halyard.core.tasks.v1.ShallowTaskList;
import com.netflix.spinnaker.halyard.deploy.deployment.v1.DeployOption;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.RunningServiceDetails;
import lombok.extern.slf4j.Slf4j;
import retrofit.RestAdapter;
import retrofit.client.OkClient;
import retrofit.converter.JacksonConverter;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@Slf4j
public class Daemon {
  public static boolean isHealthy() {
    return getService().getHealth().get("status").equalsIgnoreCase("up");
  }

  public static ShallowTaskList getTasks() {
    return getService().getTasks();
  }

  public static Supplier<Void> createBackup() {
    return () -> {
      ResponseUnwrapper.get(getService().createBackup(""));
      return null;
    };
  }

  public static Supplier<String> getCurrentDeployment() {
    return () -> ResponseUnwrapper.get(getService().getCurrentDeployment());
  }

  public static Supplier<DeploymentConfiguration> getDeploymentConfiguration(String deploymentName, boolean validate) {
    return () -> {
      Object rawDeploymentConfiguration = ResponseUnwrapper.get(getService().getDeployment(deploymentName, validate));
      return getObjectMapper().convertValue(rawDeploymentConfiguration, DeploymentConfiguration.class);
    };
  }

  public static Supplier<DeploymentEnvironment> getDeploymentEnvironment(String deploymentName, boolean validate) {
    return () -> {
      Object rawDeploymentEnvironment = ResponseUnwrapper.get(getService().getDeploymentEnvironment(deploymentName, validate));
      return getObjectMapper().convertValue(rawDeploymentEnvironment, DeploymentEnvironment.class);
    };
  }

  public static Supplier<Void> setDeploymentEnvironment(String deploymentName, boolean validate, DeploymentEnvironment deploymentEnvironment) {
    return () -> {
      ResponseUnwrapper.get(getService().setDeploymentEnvironment(deploymentName, validate, deploymentEnvironment));
      return null;
    };
  }

  public static Supplier<BakeryDefaults> getBakeryDefaults(String deploymentName, String providerName, boolean validate) {
    return () -> {
      Object rawBakeryDefaults = ResponseUnwrapper.get(getService().getBakeryDefaults(deploymentName, providerName, validate));
      return getObjectMapper().convertValue(rawBakeryDefaults, Providers.translateBakeryDefaultsType(providerName));
    };
  }

  public static Supplier<Void> setBakeryDefaults(String deploymentName, String providerName, boolean validate, BakeryDefaults bakeryDefaults) {
    return () -> {
      ResponseUnwrapper.get(getService().setBakeryDefaults(deploymentName, providerName, validate, bakeryDefaults));
      return null;
    };
  }

  public static Supplier<Features> getFeatures(String deploymentName, boolean validate) {
    return () -> {
      Object rawFeatures = ResponseUnwrapper.get(getService().getFeatures(deploymentName, validate));
      return getObjectMapper().convertValue(rawFeatures, Features.class);
    };
  }

  public static Supplier<Void> setFeatures(String deploymentName, boolean validate, Features features) {
    return () -> {
      ResponseUnwrapper.get(getService().setFeatures(deploymentName, validate, features));
      return null;
    };
  }

  public static Supplier<PersistentStorage> getPersistentStorage(String deploymentName, boolean validate) {
    return () -> {
      Object rawPersistentStorage = ResponseUnwrapper.get(getService().getPersistentStorage(deploymentName, validate));
      return getObjectMapper().convertValue(rawPersistentStorage, PersistentStorage.class);
    };
  }

  public static Supplier<PersistentStore> getPersistentStore(String deploymentName, String persistentStoreType, boolean validate) {
    return () -> {
      Object rawPersistentStore = ResponseUnwrapper.get(getService().getPersistentStore(deploymentName, persistentStoreType, validate));
      return getObjectMapper().convertValue(rawPersistentStore, PersistentStorage.translatePersistentStoreType(persistentStoreType));
    };
  }

  public static Supplier<Void> setPersistentStorage(String deploymentName, boolean validate, PersistentStorage persistentStorage) {
    return () -> {
      ResponseUnwrapper.get(getService().setPersistentStorage(deploymentName, validate, persistentStorage));
      return null;
    };
  }

  public static Supplier<Void> setPersistentStore(String deploymentName, String persistentStoreType, boolean validate, PersistentStore persistentStore) {
    return () -> {
      ResponseUnwrapper.get(getService().setPersistentStore(deploymentName, persistentStoreType, validate, persistentStore));
      return null;
    };
  }

  public static Supplier<BaseImage> getBaseImage(String deploymentName, String providerName, String baseImageId, boolean validate) {
    return () -> {
      Object rawBaseImage = ResponseUnwrapper.get(getService().getBaseImage(deploymentName, providerName, baseImageId, validate));
      return getObjectMapper().convertValue(rawBaseImage, Providers.translateBaseImageType(providerName));
    };
  }

  public static Supplier<Void> addBaseImage(String deploymentName, String providerName, boolean validate, BaseImage baseImage) {
    return () -> {
      ResponseUnwrapper.get(getService().addBaseImage(deploymentName, providerName, validate, baseImage));
      return null;
    };
  }

  public static Supplier<Void> setBaseImage(String deploymentName, String providerName, String baseImageId, boolean validate, BaseImage baseImage) {
    return () -> {
      ResponseUnwrapper.get(getService().setBaseImage(deploymentName, providerName, baseImageId, validate, baseImage));
      return null;
    };
  }

  public static Supplier<Void> deleteBaseImage(String deploymentName, String providerName, String baseImageId, boolean validate) {
    return () -> {
      ResponseUnwrapper.get(getService().deleteBaseImage(deploymentName, providerName, baseImageId, validate));
      return null;
    };
  }

  public static Supplier<Account> getAccount(String deploymentName, String providerName, String accountName, boolean validate) {
    return () -> {
      Object rawAccount = ResponseUnwrapper.get(getService().getAccount(deploymentName, providerName, accountName, validate));
      return getObjectMapper().convertValue(rawAccount, Providers.translateAccountType(providerName));
    };
  }

  public static Supplier<Void> addAccount(String deploymentName, String providerName, boolean validate, Account account) {
    return () -> {
      ResponseUnwrapper.get(getService().addAccount(deploymentName, providerName, validate, account));
      return null;
    };
  }

  public static Supplier<Void> setAccount(String deploymentName, String providerName, String accountName, boolean validate, Account account) {
    return () -> {
      ResponseUnwrapper.get(getService().setAccount(deploymentName, providerName, accountName, validate, account));
      return null;
    };
  }

  public static Supplier<Void> deleteAccount(String deploymentName, String providerName, String accountName, boolean validate) {
    return () -> {
      ResponseUnwrapper.get(getService().deleteAccount(deploymentName, providerName, accountName, validate));
      return null;
    };
  }

  public static Supplier<List<String>> getNewAccountOptions(String deploymentName, String providerName, String fieldName, Account account) {
    return () -> {
      DaemonOptions<Account> accountOptions = new DaemonOptions<Account>().setField(fieldName).setResource(account);
      return ResponseUnwrapper.get(getService().getNewAccountOptions(deploymentName, providerName, accountOptions));
    };
  }

  public static Supplier<List<String>> getExistingAccountOptions(String deploymentName, String providerName, String accountName, String fieldName) {
    return () -> {
      DaemonOptions<Void> accountOptions = new DaemonOptions<Void>().setField(fieldName);
      return ResponseUnwrapper.get(getService().getExistingAccountOptions(deploymentName, providerName, accountName, accountOptions));
    };
  }

  public static Supplier<Provider> getProvider(String deploymentName, String providerName, boolean validate) {
    return () -> {
      Object provider = ResponseUnwrapper.get(getService().getProvider(deploymentName, providerName, validate));
      return getObjectMapper().convertValue(provider, Providers.translateProviderType(providerName));
    };
  }

  public static Supplier<Void> setProviderEnableDisable(String deploymentName, String providerName, boolean validate, boolean enable) {
    return () -> {
      ResponseUnwrapper.get(getService().setProviderEnabled(deploymentName, providerName, validate, enable));
      return null;
    };
  }

  public static Supplier<Master> getMaster(String deploymentName, String ciName, String masterName, boolean validate) {
    return () -> {
      Object rawMaster = ResponseUnwrapper.get(getService().getMaster(deploymentName, ciName, masterName, validate));
      return getObjectMapper().convertValue(rawMaster, Cis.translateMasterType(ciName));
    };
  }

  public static Supplier<Void> addMaster(String deploymentName, String ciName, boolean validate, Master master) {
    return () -> {
      ResponseUnwrapper.get(getService().addMaster(deploymentName, ciName, validate, master));
      return null;
    };
  }

  public static Supplier<Void> setMaster(String deploymentName, String ciName, String masterName, boolean validate, Master master) {
    return () -> {
      ResponseUnwrapper.get(getService().setMaster(deploymentName, ciName, masterName, validate, master));
      return null;
    };
  }

  public static Supplier<Void> deleteMaster(String deploymentName, String ciName, String masterName, boolean validate) {
    return () -> {
      ResponseUnwrapper.get(getService().deleteMaster(deploymentName, ciName, masterName, validate));
      return null;
    };
  }

  public static Supplier<Ci> getCi(String deploymentName, String ciName, boolean validate) {
    return () -> {
      Object ci = ResponseUnwrapper.get(getService().getCi(deploymentName, ciName, validate));
      return getObjectMapper().convertValue(ci, Cis.translateCiType(ciName));
    };
  }

  public static Supplier<Void> setCiEnableDisable(String deploymentName, String ciName, boolean validate, boolean enable) {
    return () -> {
      ResponseUnwrapper.get(getService().setCiEnabled(deploymentName, ciName, validate, enable));
      return null;
    };
  }

  public static Supplier<Void> generateDeployment(String deploymentName, boolean validate) {
    return () -> {
      ResponseUnwrapper.get(getService().generateDeployment(deploymentName, validate, ""));
      return null;
    };
  }

  public static Supplier<RemoteAction> deployDeployment(String deploymentName, boolean validate, List<DeployOption> deployOptions, List<String> serviceNames) {
    return () -> {
      Object rawDeployResult = ResponseUnwrapper.get(getService().deployDeployment(deploymentName, validate, deployOptions, serviceNames, ""));
      return getObjectMapper().convertValue(rawDeployResult, RemoteAction.class);
    };
  }

  public static Supplier<Void> cleanDeployment(String deploymentName, boolean validate) {
    return () -> {
      ResponseUnwrapper.get(getService().cleanDeployment(deploymentName, validate, ""));
      return null;
    };
  }

  public static Supplier<Void> rollbackDeployment(String deploymentName, boolean validate, List<String> serviceNames) {
    return () -> {
      ResponseUnwrapper.get(getService().rollbackDeployment(deploymentName, validate, serviceNames, ""));
      return null;
    };
  }

  public static Supplier<NodeDiff> configDiff(String deploymentName, boolean validate) {
    return () -> {
      Object rawDiff = ResponseUnwrapper.get(getService().configDiff(deploymentName, validate));
      return getObjectMapper().convertValue(rawDiff, NodeDiff.class);
    };
  }

  public static Supplier<MetricStores> getMetricStores(String deploymentName, boolean validate) {
    return () -> {
      Object rawMetricStores = ResponseUnwrapper.get(getService().getMetricStores(deploymentName, validate));
      return getObjectMapper().convertValue(rawMetricStores, MetricStores.class);
    };
  }

  public static Supplier<Void> setMetricStores(String deploymentName, boolean validate, MetricStores metricStores) {
    return () -> {
      ResponseUnwrapper.get(getService().setMetricStores(deploymentName, validate, metricStores));
      return null;
    };
  }

  public static Supplier<MetricStore> getMetricStore(String deploymentName, String metricStoreType, boolean validate) {
    return () -> {
      Object rawMetricStore = ResponseUnwrapper.get(getService().getMetricStore(deploymentName, metricStoreType, validate));
      return getObjectMapper().convertValue(rawMetricStore, MetricStores.translateMetricStoreType(metricStoreType));
    };
  }

  public static Supplier<Void> setMetricStore(String deploymentName, String metricStoreType, boolean validate, MetricStore metricStore) {
    return () -> {
      Map translatedMetricStore = getObjectMapper().convertValue(metricStore, Map.class);
      ResponseUnwrapper.get(getService().setMetricStore(deploymentName, metricStoreType, validate, translatedMetricStore));
      return null;
    };
  }

  public static Supplier<Void> setMetricStoreEnabled(String deploymentName, String metricStoreType, boolean validate, boolean enabled) {
    return () -> {
      ResponseUnwrapper.get(getService().setMetricStoreEnabled(deploymentName, metricStoreType, validate, enabled));
      return null;
    };
  }

  public static Supplier<Security> getSecurity(String deploymentName, boolean validate) {
    return () -> {
      Object rawSecurity = ResponseUnwrapper.get(getService().getSecurity(deploymentName, validate));
      return getObjectMapper().convertValue(rawSecurity, Security.class);
    };
  }

  public static Supplier<Void> setSecurity(String deploymentName, boolean validate, Security security) {
    return () -> {
      ResponseUnwrapper.get(getService().setSecurity(deploymentName, validate, security));
      return null;
    };
  }

  public static Supplier<ApiSecurity> getApiSecurity(String deploymentName, boolean validate) {
    return () -> {
      Object rawApiSecurity = ResponseUnwrapper.get(getService().getApiSecurity(deploymentName, validate));
      return getObjectMapper().convertValue(rawApiSecurity, ApiSecurity.class);
    };
  }

  public static Supplier<Void> setApiSecurity(String deploymentName, boolean validate, ApiSecurity apiSecurity) {
    return () -> {
      ResponseUnwrapper.get(getService().setApiSecurity(deploymentName, validate, apiSecurity));
      return null;
    };
  }

  public static Supplier<SpringSsl> getSpringSsl(String deploymentName, boolean validate) {
    return () -> {
      Object rawSpringSsl = ResponseUnwrapper.get(getService().getSpringSsl(deploymentName, validate));
      return getObjectMapper().convertValue(rawSpringSsl, SpringSsl.class);
    };
  }

  public static Supplier<Void> setSpringSsl(String deploymentName, boolean validate, SpringSsl apacheSsl) {
    return () -> {
      ResponseUnwrapper.get(getService().setSpringSsl(deploymentName, validate, apacheSsl));
      return null;
    };
  }

  public static Supplier<Void> setSpringSslEnabled(String deploymentName, boolean validate, boolean enabled) {
    return () -> {
      ResponseUnwrapper.get(getService().setSpringSslEnabled(deploymentName, validate, enabled));
      return null;
    };
  }

  public static Supplier<UiSecurity> getUiSecurity(String deploymentName, boolean validate) {
    return () -> {
      Object rawUiSecurity = ResponseUnwrapper.get(getService().getUiSecurity(deploymentName, validate));
      return getObjectMapper().convertValue(rawUiSecurity, UiSecurity.class);
    };
  }

  public static Supplier<Void> setUiSecurity(String deploymentName, boolean validate, UiSecurity uiSecurity) {
    return () -> {
      ResponseUnwrapper.get(getService().setUiSecurity(deploymentName, validate, uiSecurity));
      return null;
    };
  }

  public static Supplier<ApacheSsl> getApacheSsl(String deploymentName, boolean validate) {
    return () -> {
      Object rawApacheSsl = ResponseUnwrapper.get(getService().getApacheSsl(deploymentName, validate));
      return getObjectMapper().convertValue(rawApacheSsl, ApacheSsl.class);
    };
  }

  public static Supplier<Void> setApacheSsl(String deploymentName, boolean validate, ApacheSsl apacheSsl) {
    return () -> {
      ResponseUnwrapper.get(getService().setApacheSsl(deploymentName, validate, apacheSsl));
      return null;
    };
  }

  public static Supplier<Void> setApacheSslEnabled(String deploymentName, boolean validate, boolean enabled) {
    return () -> {
      ResponseUnwrapper.get(getService().setApacheSslEnabled(deploymentName, validate, enabled));
      return null;
    };
  }

  public static Supplier<AuthnMethod> getAuthnMethod(String deploymentName, String methodName, boolean validate) {
    return () -> {
      Object rawOAuth = ResponseUnwrapper.get(getService().getAuthnMethod(deploymentName, methodName, validate));
      return getObjectMapper().convertValue(rawOAuth, AuthnMethod.translateAuthnMethodName(methodName));
    };
  }

  public static Supplier<Void> setAuthnMethod(String deploymentName, String methodName, boolean validate, AuthnMethod authnMethod) {
    return () -> {
      ResponseUnwrapper.get(getService().setAuthnMethod(deploymentName, methodName, validate, authnMethod));
      return null;
    };
  }

  public static Supplier<Void> setGroupMembership(String deploymentName, boolean validate, GroupMembership membership) {
    return () -> {
      ResponseUnwrapper.get(getService().setGroupMembership(deploymentName, validate, membership));
      return null;
    };
  }

  public static Supplier<GroupMembership> getGroupMembership(String deploymentName, boolean validate) {
    return () -> {
      Object rawGroupMembership = ResponseUnwrapper.get(getService().getGroupMembership(deploymentName, validate));
      return getObjectMapper().convertValue(rawGroupMembership, GroupMembership.class);
    };
  }

  public static Supplier<RoleProvider> getRoleProvider(String deploymentName, String roleProviderName, boolean validate) {
    return () -> {
      Object rawRoleProvider = ResponseUnwrapper.get(getService().getRoleProvider(deploymentName, roleProviderName, validate));
      return getObjectMapper().convertValue(rawRoleProvider, GroupMembership.translateRoleProviderType(roleProviderName));
    };
  }

  public static Supplier<Void> setRoleProvider(String deploymentName, String roleProviderName, boolean validate, RoleProvider authnMethod) {
    return () -> {
      ResponseUnwrapper.get(getService().setRoleProvider(deploymentName, roleProviderName, validate, authnMethod));
      return null;
    };
  }

  public static Supplier<Void> setAuthzEnabled(String deploymentName, boolean validate, boolean enabled) {
    return () -> {
      ResponseUnwrapper.get(getService().setAuthzEnabled(deploymentName, validate, enabled));
      return null;
    };
  }

  public static Supplier<Void> setAuthnMethodEnabled(String deploymentName, String methodName, boolean validate, boolean enabled) {
    return () -> {
      ResponseUnwrapper.get(getService().setAuthnMethodEnabled(deploymentName, methodName, validate, enabled));
      return null;
    };
  }

  public static Supplier<Versions> getVersions() {
    return () -> {
      Object rawVersions = ResponseUnwrapper.get(getService().getVersions());
      return getObjectMapper().convertValue(rawVersions, Versions.class);
    };
  }

  public static Supplier<String> getLatest() {
    return () -> ResponseUnwrapper.get(getService().getLatest());
  }

  public static Supplier<RunningServiceDetails> getServiceDetails(String deploymentName, String serviceName, boolean validate) {
    return () -> {
      Object rawDetails = ResponseUnwrapper.get(getService().getServiceDetails(deploymentName, serviceName, validate));
      return getObjectMapper().convertValue(rawDetails, RunningServiceDetails.class);
    };
  }

  public static Supplier<BillOfMaterials> getBillOfMaterials(String version) {
    return () -> {
      Object rawBillOfMaterials = ResponseUnwrapper.get(getService().getBillOfMaterials(version));
      return getObjectMapper().convertValue(rawBillOfMaterials, BillOfMaterials.class);
    };
  }

  public static Supplier<Void> publishProfile(String bomPath, String artifactName, String profilePath) {
    return () -> {
      ResponseUnwrapper.get(getService().publishProfile(bomPath, artifactName, profilePath, ""));
      return null;
    };
  }

  public static Supplier<Void> publishLatest(String latest) {
    return () -> {
      ResponseUnwrapper.get(getService().publishLatest(latest, ""));
      return null;
    };
  }

  public static Supplier<Void> deprecateVersion(Versions.Version version) {
    return () -> {
      ResponseUnwrapper.get(getService().deprecateVersion(version));
      return null;
    };
  }

  public static Supplier<Void> publishVersion(Versions.Version version) {
    return () -> {
      ResponseUnwrapper.get(getService().publishVersion(version));
      return null;
    };
  }

  public static Supplier<Void> publishBom(String bomPath) {
    return () -> {
      ResponseUnwrapper.get(getService().publishBom(bomPath,""));
      return null;
    };
  }

  public static Supplier<String> getVersion(String deploymentName, boolean validate) {
    return () -> ResponseUnwrapper.get(getService().getVersion(deploymentName, validate));
  }

  public static Supplier<Void> setVersion(String deploymentName, boolean validate, String versionName) {
    return () -> {
      Versions.Version version = new Versions.Version().setVersion(versionName);
      ResponseUnwrapper.get(getService().setVersion(deploymentName, validate, version));
      return null;
    };
  }

  static <C, T> DaemonTask<C, T> getTask(String uuid) {
    return getService().getTask(uuid);
  }

  public static void interruptTask(String uuid) {
    getService().interruptTask(uuid, "");
  }

  private static DaemonService getService() {
    if (service == null) {
      boolean debug =  GlobalOptions.getGlobalOptions().isDebug();
      service = createService(debug);
    }

    return service;
  }

  private static ObjectMapper getObjectMapper() {
    if (objectMapper == null) {
      objectMapper = new ObjectMapper();
      objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    return objectMapper;
  }

  // TODO(lwander): setup config file for this
  static final private String endpoint = "http://localhost:8064";

  static private DaemonService service;
  static private ObjectMapper objectMapper;

  private static DaemonService createService(boolean log) {
    return new RestAdapter.Builder()
        .setEndpoint(endpoint)
        .setClient(new OkClient())
        .setConverter(new JacksonConverter(getObjectMapper()))
        .setLogLevel(log ? RestAdapter.LogLevel.FULL : RestAdapter.LogLevel.NONE)
        .build()
        .create(DaemonService.class);
  }
}

