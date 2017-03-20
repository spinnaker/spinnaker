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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.halyard.cli.command.v1.GlobalOptions;
import com.netflix.spinnaker.halyard.config.model.v1.node.*;
import com.netflix.spinnaker.halyard.config.model.v1.security.AuthnMethod;
import com.netflix.spinnaker.halyard.config.model.v1.security.GroupMembership;
import com.netflix.spinnaker.halyard.config.model.v1.security.RoleProvider;
import com.netflix.spinnaker.halyard.config.model.v1.security.Security;
import com.netflix.spinnaker.halyard.core.RemoteAction;
import com.netflix.spinnaker.halyard.core.registry.v1.BillOfMaterials;
import com.netflix.spinnaker.halyard.core.registry.v1.Versions;
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTask;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.RunningServiceDetails;
import lombok.extern.slf4j.Slf4j;
import retrofit.RestAdapter;
import retrofit.client.OkClient;

import java.util.List;
import java.util.function.Supplier;

@Slf4j
public class Daemon {
  public static List<String> getTasks() {
    return getService().getTasks();
  }

  public static boolean isHealthy() {
    return getService().getHealth().get("status").equalsIgnoreCase("up");
  }

  public static Supplier<String> getCurrentDeployment() {
    return () -> ResponseUnwrapper.get(getService().getCurrentDeployment());
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
      Object rawStorage = ResponseUnwrapper.get(getService().getPersistentStorage(deploymentName, validate));
      return getObjectMapper().convertValue(rawStorage, PersistentStorage.class);
    };
  }

  public static Supplier<Void> setPersistentStorage(String deploymentName, boolean validate, PersistentStorage persistentStorage) {
    return () -> {
      ResponseUnwrapper.get(getService().setPersistentStorage(deploymentName, validate, persistentStorage));
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

  public static Supplier<Master> getMaster(String deploymentName, String webhookName, String masterName, boolean validate) {
    return () -> {
      Object rawMaster = ResponseUnwrapper.get(getService().getMaster(deploymentName, webhookName, masterName, validate));
      return getObjectMapper().convertValue(rawMaster, Webhooks.translateMasterType(webhookName));
    };
  }

  public static Supplier<Void> addMaster(String deploymentName, String webhookName, boolean validate, Master master) {
    return () -> {
      ResponseUnwrapper.get(getService().addMaster(deploymentName, webhookName, validate, master));
      return null;
    };
  }

  public static Supplier<Void> setMaster(String deploymentName, String webhookName, String masterName, boolean validate, Master master) {
    return () -> {
      ResponseUnwrapper.get(getService().setMaster(deploymentName, webhookName, masterName, validate, master));
      return null;
    };
  }

  public static Supplier<Void> deleteMaster(String deploymentName, String webhookName, String masterName, boolean validate) {
    return () -> {
      ResponseUnwrapper.get(getService().deleteMaster(deploymentName, webhookName, masterName, validate));
      return null;
    };
  }

  public static Supplier<Webhook> getWebhook(String deploymentName, String webhookName, boolean validate) {
    return () -> {
      Object webhook = ResponseUnwrapper.get(getService().getWebhook(deploymentName, webhookName, validate));
      return getObjectMapper().convertValue(webhook, Webhooks.translateWebhookType(webhookName));
    };
  }

  public static Supplier<Void> setWebhookEnableDisable(String deploymentName, String webhookName, boolean validate, boolean enable) {
    return () -> {
      ResponseUnwrapper.get(getService().setWebhookEnabled(deploymentName, webhookName, validate, enable));
      return null;
    };
  }

  public static Supplier<Void> generateDeployment(String deploymentName, boolean validate) {
    return () -> {
      ResponseUnwrapper.get(getService().generateDeployment(deploymentName, validate, ""));
      return null;
    };
  }

  public static Supplier<RemoteAction> deployDeployment(String deploymentName, boolean validate, boolean installOnly) {
    return () -> {
      Object rawDeployResult = ResponseUnwrapper.get(getService().deployDeployment(deploymentName, validate, installOnly, ""));
      return getObjectMapper().convertValue(rawDeployResult, RemoteAction.class);
    };
  }

  public static Supplier<NodeDiff> configDiff(String deploymentName, boolean validate) {
    return () -> {
      Object rawDiff = ResponseUnwrapper.get(getService().configDiff(deploymentName, validate));
      return getObjectMapper().convertValue(rawDiff, NodeDiff.class);
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

  public static Supplier<AuthnMethod> getAuthnMethod(String deploymentName, String methodName, boolean validate) {
    return () -> {
      Object rawOAuth2 = ResponseUnwrapper.get(getService().getAuthnMethod(deploymentName, methodName, validate));
      return getObjectMapper().convertValue(rawOAuth2, AuthnMethod.translateAuthnMethodName(methodName));
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
        .setLogLevel(log ? RestAdapter.LogLevel.FULL : RestAdapter.LogLevel.NONE)
        .build()
        .create(DaemonService.class);
  }
}

