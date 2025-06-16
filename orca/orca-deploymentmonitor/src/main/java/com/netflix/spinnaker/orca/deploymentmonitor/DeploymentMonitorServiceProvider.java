/*
 * Copyright 2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.orca.deploymentmonitor;

import com.netflix.spinnaker.config.DeploymentMonitorDefinition;
import com.netflix.spinnaker.kork.exceptions.UserException;
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerRetrofitErrorHandler;
import com.netflix.spinnaker.orca.retrofit.logging.RetrofitSlf4jLog;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import retrofit.RequestInterceptor;
import retrofit.RestAdapter;
import retrofit.client.Client;
import retrofit.converter.JacksonConverter;

public class DeploymentMonitorServiceProvider {
  private static final Logger log = LoggerFactory.getLogger(DeploymentMonitorServiceProvider.class);

  private Client retrofitClient;
  private RestAdapter.LogLevel retrofitLogLevel;
  private RequestInterceptor spinnakerRequestInterceptor;
  private List<DeploymentMonitorDefinition> deploymentMonitors;
  private HashMap<String, DeploymentMonitorService> serviceCache;

  public DeploymentMonitorServiceProvider(
      Client retrofitClient,
      RestAdapter.LogLevel retrofitLogLevel,
      RequestInterceptor spinnakerRequestInterceptor,
      List<DeploymentMonitorDefinition> deploymentMonitors) {
    this.retrofitClient = retrofitClient;
    this.retrofitLogLevel = retrofitLogLevel;
    this.spinnakerRequestInterceptor = spinnakerRequestInterceptor;
    this.deploymentMonitors = deploymentMonitors;
    this.serviceCache = new HashMap<>();

    log.info(
        "Found the following deployment monitors: "
            + deploymentMonitors.stream().map(x -> x.getName()).collect(Collectors.joining(", ")));
  }

  public DeploymentMonitorDefinition getDefinitionById(String id) {
    DeploymentMonitorDefinition definition =
        deploymentMonitors.stream()
            .filter(x -> x.getId().equals(id))
            .findFirst()
            .orElseThrow(() -> new UserException("Deployment monitor not configured, ID: " + id));

    if (definition.getService() == null) {
      definition.setService(getServiceByDefinition(definition));
    }

    return definition;
  }

  private synchronized DeploymentMonitorService getServiceByDefinition(
      DeploymentMonitorDefinition definition) {
    if (!serviceCache.containsKey(definition.getId())) {
      log.info("Instantiating deployment monitor {} -> {}", definition, definition.getBaseUrl());

      DeploymentMonitorService service =
          new RestAdapter.Builder()
              .setRequestInterceptor(spinnakerRequestInterceptor)
              .setEndpoint(definition.getBaseUrl())
              .setClient(retrofitClient)
              .setLogLevel(retrofitLogLevel)
              .setLog(new RetrofitSlf4jLog(DeploymentMonitorService.class))
              .setConverter(new JacksonConverter())
              .setErrorHandler(SpinnakerRetrofitErrorHandler.getInstance())
              .build()
              .create(DeploymentMonitorService.class);

      serviceCache.put(definition.getId(), service);
    }

    return serviceCache.get(definition.getId());
  }
}
