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
import com.netflix.spinnaker.halyard.config.model.v1.security.OAuth2;
import com.netflix.spinnaker.halyard.config.model.v1.security.Security;
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTask;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.Versions;
import retrofit.RestAdapter;
import retrofit.client.OkClient;

public class Daemon {
  public static String getCurrentDeployment() {
    return ResponseUnwrapper.get(getService().getCurrentDeployment());
  }

  public static DeploymentEnvironment getDeploymentEnvironment(String deploymentName, boolean validate) {
    Object rawDeploymentEnvironment = ResponseUnwrapper.get(getService().getDeploymentEnvironment(deploymentName, validate));
    return getObjectMapper().convertValue(rawDeploymentEnvironment, DeploymentEnvironment.class);
  }

  public static void setDeploymentEnvironment(String deploymentName, boolean validate, DeploymentEnvironment deploymentEnvironment) {
    ResponseUnwrapper.get(getService().setDeploymentEnvironment(deploymentName, validate, deploymentEnvironment));
  }

  public static BakeryDefaults getBakeryDefaults(String deploymentName, String providerName, boolean validate) {
    Object rawBakeryDefaults = ResponseUnwrapper.get(getService().getBakeryDefaults(deploymentName, providerName, validate));
    return getObjectMapper().convertValue(rawBakeryDefaults, BakeryDefaults.class);
  }

  public static void setBakeryDefaults(String deploymentName, String providerName, boolean validate, BakeryDefaults bakeryDefaults) {
    ResponseUnwrapper.get(getService().setBakeryDefaults(deploymentName, providerName, validate, bakeryDefaults));
  }

  public static Features getFeatures(String deploymentName, boolean validate) {
    Object rawFeatures = ResponseUnwrapper.get(getService().getFeatures(deploymentName, validate));
    return getObjectMapper().convertValue(rawFeatures, Features.class);
  }

  public static void setFeatures(String deploymentName, boolean validate, Features features) {
    ResponseUnwrapper.get(getService().setFeatures(deploymentName, validate, features));
  }

  public static PersistentStorage getPersistentStorage(String deploymentName, boolean validate) {
    Object rawStorage = ResponseUnwrapper.get(getService().getPersistentStorage(deploymentName, validate));
    return getObjectMapper().convertValue(rawStorage, PersistentStorage.class);
  }

  public static void setPersistentStorage(String deploymentName, boolean validate, PersistentStorage persistentStorage) {
    ResponseUnwrapper.get(getService().setPersistentStorage(deploymentName, validate, persistentStorage));
  }

  public static BaseImage getBaseImage(String deploymentName, String providerName, String baseImageId, boolean validate) {
    Object rawBaseImage = ResponseUnwrapper.get(getService().getBaseImage(deploymentName, providerName, baseImageId, validate));
    return getObjectMapper().convertValue(rawBaseImage, Providers.translateBaseImageType(providerName));
  }

  public static void addBaseImage(String deploymentName, String providerName, boolean validate, BaseImage baseImage) {
    ResponseUnwrapper.get(getService().addBaseImage(deploymentName, providerName, validate, baseImage));
  }

  public static void setBaseImage(String deploymentName, String providerName, String baseImageId, boolean validate, BaseImage baseImage) {
    ResponseUnwrapper.get(getService().setBaseImage(deploymentName, providerName, baseImageId, validate, baseImage));
  }

  public static void deleteBaseImage(String deploymentName, String providerName, String baseImageId, boolean validate) {
    ResponseUnwrapper.get(getService().deleteBaseImage(deploymentName, providerName, baseImageId, validate));
  }

  public static Account getAccount(String deploymentName, String providerName, String accountName, boolean validate) {
    Object rawAccount = ResponseUnwrapper.get(getService().getAccount(deploymentName, providerName, accountName, validate));
    return getObjectMapper().convertValue(rawAccount, Providers.translateAccountType(providerName));
  }

  public static void addAccount(String deploymentName, String providerName, boolean validate, Account account) {
    ResponseUnwrapper.get(getService().addAccount(deploymentName, providerName, validate, account));
  }

  public static void setAccount(String deploymentName, String providerName, String accountName, boolean validate, Account account) {
    ResponseUnwrapper.get(getService().setAccount(deploymentName, providerName, accountName, validate, account));
  }

  public static void deleteAccount(String deploymentName, String providerName, String accountName, boolean validate) {
    ResponseUnwrapper.get(getService().deleteAccount(deploymentName, providerName, accountName, validate));
  }

  public static Provider getProvider(String deploymentName, String providerName, boolean validate) {
    Object provider = ResponseUnwrapper.get(getService().getProvider(deploymentName, providerName, validate));
    return getObjectMapper().convertValue(provider, Providers.translateProviderType(providerName));
  }

  public static void setProviderEnableDisable(String deploymentName, String providerName, boolean validate, boolean enable) {
    ResponseUnwrapper.get(getService().setProviderEnabled(deploymentName, providerName, validate, enable));
  }

  public static void generateDeployment(String deploymentName, boolean validate) {
    ResponseUnwrapper.get(getService().generateDeployment(deploymentName, validate, ""));
  }

  public static void deployDeployment(String deploymentName, boolean validate) {
    ResponseUnwrapper.get(getService().deployDeployment(deploymentName, validate, ""));
  }

  public static String deployDeploymentPlan(String deploymentName, boolean validate) {
    return ResponseUnwrapper.get(getService().deployDeploymentPlan(deploymentName, validate));
  }

  public static Security getSecurity(String deploymentName, boolean validate) {
    Object rawSecurity = ResponseUnwrapper.get(getService().getSecurity(deploymentName, validate));
    return getObjectMapper().convertValue(rawSecurity, Security.class);
  }

  public static void setSecurity(String deploymentName, boolean validate, Security security) {
    ResponseUnwrapper.get(getService().setSecurity(deploymentName, validate, security));
  }

  public static OAuth2 getOAuth2(String deploymentName, boolean validate) {
    Object rawOAuth2 = ResponseUnwrapper.get(getService().getOAuth2(deploymentName, validate));
    return getObjectMapper().convertValue(rawOAuth2, OAuth2.class);
  }

  public static void setOAuth2(String deploymentName, boolean validate, OAuth2 oauth2) {
    ResponseUnwrapper.get(getService().setOAuth2(deploymentName, validate, oauth2));
  }

  public static <C, T> DaemonTask<C, T> getTask(String uuid) {
    return getService().getTask(uuid);
  }

  public static Versions getVersions() {
    Object rawVersions = ResponseUnwrapper.get(getService().getVersions());
    return getObjectMapper().convertValue(rawVersions, Versions.class);
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

