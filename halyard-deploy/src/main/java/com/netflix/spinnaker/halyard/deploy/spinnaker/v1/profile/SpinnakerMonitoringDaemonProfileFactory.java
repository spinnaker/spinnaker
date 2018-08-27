/*
 * Copyright 2017 Google, Inc.
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

package com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.netflix.spinnaker.halyard.config.model.v1.metricStores.datadog.DatadogStore;
import com.netflix.spinnaker.halyard.config.model.v1.metricStores.prometheus.PrometheusStore;
import com.netflix.spinnaker.halyard.config.model.v1.metricStores.stackdriver.StackdriverStore;
import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentConfiguration;
import com.netflix.spinnaker.halyard.config.model.v1.node.MetricStores;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerArtifact;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerRuntimeSettings;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.ServiceSettings;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.SpinnakerService.Type;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
@Component
public class SpinnakerMonitoringDaemonProfileFactory extends RegistryBackedProfileFactory {
  @Override
  public String commentPrefix() {
    return "## ";
  }

  @Override
  public SpinnakerArtifact getArtifact() {
    return SpinnakerArtifact.SPINNAKER_MONITORING_DAEMON;
  }

  @Override
  protected void setProfile(Profile profile, DeploymentConfiguration deploymentConfiguration, SpinnakerRuntimeSettings endpoints) {
    ServiceSettings monitoringService = endpoints.getServiceSettings(Type.MONITORING_DAEMON);
    MetricStores metricStores = deploymentConfiguration.getMetricStores();
    List<String> enabledMetricStores = new ArrayList<>();
    List<String> files = new ArrayList<>();

    DatadogStore datadogStore = metricStores.getDatadog();
    if (datadogStore.isEnabled()) {
      enabledMetricStores.add("datadog");
    }

    PrometheusStore prometheusStore = metricStores.getPrometheus();
    if (prometheusStore.isEnabled()) {
      enabledMetricStores.add("prometheus");
    }

    StackdriverStore stackdriverStore = metricStores.getStackdriver();
    if (stackdriverStore.isEnabled()) {
      enabledMetricStores.add("stackdriver");
      files.addAll(backupRequiredFiles(stackdriverStore, deploymentConfiguration.getName()));
    }

    profile.appendContents(yamlToString(metricStores));

    Server server = new Server()
        .setHost(monitoringService.getHost())
        .setPort(monitoringService.getPort());

    ServerConfig serverConfig = new ServerConfig();
    serverConfig.setServer(server);

    profile.appendContents(yamlToString(serverConfig));

    Monitor monitor = new Monitor()
        .setPeriod(metricStores.getPeriod())
        .setMetricStore(enabledMetricStores);

    MonitorConfig monitorConfig = new MonitorConfig();
    monitorConfig.setMonitor(monitor);

    profile.appendContents(yamlToString(monitorConfig));
    profile.appendContents(profile.getBaseContents());
    profile.setRequiredFiles(files);
  }

  @Data
  private static class Server {
    String host;
    int port;
  }

  @Data
  private static class ServerConfig {
    Server server;
  }

  @Data
  private static class Monitor {
    int period;

    @JsonProperty("metric_store")
    List<String> metricStore = new ArrayList<>();
  }

  @Data
  private static class MonitorConfig {
    Monitor monitor;
  }
}
