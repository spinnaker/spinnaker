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
import com.netflix.spinnaker.halyard.config.model.v1.node.Account;
import com.netflix.spinnaker.halyard.config.model.v1.node.Features;
import com.netflix.spinnaker.halyard.config.model.v1.node.Provider;
import com.netflix.spinnaker.halyard.config.model.v1.node.Providers;
import com.netflix.spinnaker.halyard.config.spinnaker.v1.Versions;
import retrofit.RestAdapter;
import retrofit.client.OkClient;

public class Daemon {
  public static String getCurrentDeployment() {
    return ResponseUnwrapper.get(getService().getCurrentDeployment());
  }

  public static Features getFeatures(String deploymentName, boolean validate) {
    return ResponseUnwrapper.get(service.getFeatures(deploymentName, validate));
  }

  public static void setFeatures(String deploymentName, boolean validate, Features features) {
    ResponseUnwrapper.get(service.setFeatures(deploymentName, validate, features));
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

  public static Versions getVersions() {
    return ResponseUnwrapper.get(getService().getVersions());
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

