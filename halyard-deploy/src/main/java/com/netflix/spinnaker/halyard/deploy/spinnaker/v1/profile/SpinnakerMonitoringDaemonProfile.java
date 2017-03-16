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
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerEndpoints;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.SpinnakerMonitoringDaemonService;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.SpinnakerService;
import io.fabric8.utils.Strings;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
@Component
public class SpinnakerMonitoringDaemonProfile extends SpinnakerProfile {
  private String profileFileName = "spinnaker-monitoring.yml";

  @Override
  public String commentPrefix() {
    return "## ";
  }

  @Override
  public SpinnakerArtifact getArtifact() {
    return SpinnakerArtifact.SPINNAKER_MONITORING_DAEMON;
  }

  @Override
  protected ProfileConfig generateFullConfig(ProfileConfig config, DeploymentConfiguration deploymentConfiguration, SpinnakerEndpoints endpoints) {
    String primaryConfig = config.getPrimaryConfigFile();
    SpinnakerEndpoints.Services services = endpoints.getServices();
    SpinnakerMonitoringDaemonService monitoringService = services.getSpinnakerMonitoringDaemon();
    MetricStores metricStores = deploymentConfiguration.getMetricStores();
    List<String> enabledMetricStores = new ArrayList<>();

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
    }

    config.extendConfig(primaryConfig, yamlToString(metricStores));

    Server server = new Server()
        .setHost(monitoringService.getHost())
        .setPort(monitoringService.getPort());

    ServerConfig serverConfig = new ServerConfig();
    serverConfig.setServer(server);

    config.extendConfig(primaryConfig, yamlToString(serverConfig));

    Monitor monitor = new Monitor()
        .setPeriod(metricStores.getPeriod())
        .setMetricStore(enabledMetricStores);

    MonitorConfig monitorConfig = new MonitorConfig();
    monitorConfig.setMonitor(monitor);

    config.extendConfig(primaryConfig, yamlToString(monitorConfig));

    for (SpinnakerService spinnakerService : services.allServices()) {
      if (!spinnakerService.isMonitoringEnabled()) {
        continue;
      }

      // The tacit assumption is that the monitoring daemon will always be installed
      // on whatever machine the service it's monitoring is running.
      // TODO(lwander) configure base auth when needed
      String metricsConfig = "metrics_url: http://localhost:" + spinnakerService.getPort() + "/spectator/metrics";
      String serviceName = spinnakerService.getName();
      String configPath = Strings.join(File.separator, "registry", serviceName) + ".yml";
      config.setConfig(configPath, metricsConfig);
    }

    return config;
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
