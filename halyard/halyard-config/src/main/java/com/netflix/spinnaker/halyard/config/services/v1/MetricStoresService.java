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

package com.netflix.spinnaker.halyard.config.services.v1;

import com.netflix.spinnaker.halyard.config.model.v1.metricStores.datadog.DatadogStore;
import com.netflix.spinnaker.halyard.config.model.v1.metricStores.newrelic.NewrelicStore;
import com.netflix.spinnaker.halyard.config.model.v1.metricStores.prometheus.PrometheusStore;
import com.netflix.spinnaker.halyard.config.model.v1.metricStores.stackdriver.StackdriverStore;
import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentConfiguration;
import com.netflix.spinnaker.halyard.config.model.v1.node.MetricStore;
import com.netflix.spinnaker.halyard.config.model.v1.node.MetricStores;
import com.netflix.spinnaker.halyard.config.model.v1.node.NodeFilter;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemBuilder;
import com.netflix.spinnaker.halyard.core.error.v1.HalException;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem.Severity;
import com.netflix.spinnaker.halyard.core.problem.v1.ProblemSet;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class MetricStoresService {
  @Autowired private LookupService lookupService;

  @Autowired private DeploymentService deploymentService;

  @Autowired private ValidateService validateService;

  public MetricStores getMetricStores(String deploymentName) {
    NodeFilter filter = new NodeFilter().setDeployment(deploymentName).setMetricStores();

    List<MetricStores> matching = lookupService.getMatchingNodesOfType(filter, MetricStores.class);

    switch (matching.size()) {
      case 0:
        MetricStores metricStores = new MetricStores();
        setMetricStores(deploymentName, metricStores);
        return metricStores;
      case 1:
        return matching.get(0);
      default:
        throw new RuntimeException(
            "It shouldn't be possible to have multiple metricStores nodes. This is a bug.");
    }
  }

  public void setMetricStoreEnabled(
      String deploymentName, String metricStoreType, boolean enabled) {
    MetricStore metricStore = getMetricStore(deploymentName, metricStoreType);
    metricStore.setEnabled(enabled);
    setMetricStore(deploymentName, metricStore);
  }

  public MetricStore getMetricStore(String deploymentName, String metricStoreType) {
    NodeFilter filter =
        new NodeFilter()
            .setDeployment(deploymentName)
            .setMetricStores()
            .setMetricStore(metricStoreType);

    List<MetricStore> matching = lookupService.getMatchingNodesOfType(filter, MetricStore.class);

    try {
      switch (matching.size()) {
        case 0:
          MetricStore metricStores =
              MetricStores.translateMetricStoreType(metricStoreType).newInstance();
          setMetricStore(deploymentName, metricStores);
          return metricStores;
        case 1:
          return matching.get(0);
        default:
          throw new RuntimeException(
              "It shouldn't be possible to have multiple metricStore nodes of the same type. This is a bug.");
      }
    } catch (InstantiationException | IllegalAccessException e) {
      throw new HalException(
          new ConfigProblemBuilder(
                  Severity.FATAL,
                  "Can't create an empty metric store node "
                      + "for metricStore type \""
                      + metricStoreType
                      + "\"")
              .build());
    }
  }

  public void setMetricStores(String deploymentName, MetricStores newMetricStores) {
    DeploymentConfiguration deploymentConfiguration =
        deploymentService.getDeploymentConfiguration(deploymentName);
    deploymentConfiguration.setMetricStores(newMetricStores);
  }

  public void setMetricStore(String deploymentName, MetricStore metricStore) {
    MetricStores metricStores = getMetricStores(deploymentName);
    switch (metricStore.getMetricStoreType()) {
      case DATADOG:
        metricStores.setDatadog((DatadogStore) metricStore);
        break;
      case NEWRELIC:
        metricStores.setNewrelic((NewrelicStore) metricStore);
        break;
      case PROMETHEUS:
        metricStores.setPrometheus((PrometheusStore) metricStore);
        break;
      case STACKDRIVER:
        metricStores.setStackdriver((StackdriverStore) metricStore);
        break;
      default:
        throw new RuntimeException("Unknown Metric Store " + metricStore.getMetricStoreType());
    }
  }

  public ProblemSet validateMetricStores(String deploymentName) {
    NodeFilter filter = new NodeFilter().setDeployment(deploymentName).setMetricStores();
    return validateService.validateMatchingFilter(filter);
  }

  public ProblemSet validateMetricStore(String deploymentName, String metricStoreType) {
    NodeFilter filter =
        new NodeFilter()
            .setDeployment(deploymentName)
            .setMetricStores()
            .setMetricStore(metricStoreType);
    return validateService.validateMatchingFilter(filter);
  }
}
