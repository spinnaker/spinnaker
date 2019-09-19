/*
 * Copyright 2018 Google, Inc.
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

package com.netflix.spinnaker.halyard.config.model.v1.canary;

import com.google.common.collect.Lists;
import com.netflix.spinnaker.halyard.config.model.v1.canary.aws.AwsCanaryAccount;
import com.netflix.spinnaker.halyard.config.model.v1.canary.aws.AwsCanaryServiceIntegration;
import com.netflix.spinnaker.halyard.config.model.v1.canary.datadog.DatadogCanaryAccount;
import com.netflix.spinnaker.halyard.config.model.v1.canary.datadog.DatadogCanaryServiceIntegration;
import com.netflix.spinnaker.halyard.config.model.v1.canary.google.GoogleCanaryAccount;
import com.netflix.spinnaker.halyard.config.model.v1.canary.google.GoogleCanaryServiceIntegration;
import com.netflix.spinnaker.halyard.config.model.v1.canary.newrelic.NewRelicCanaryAccount;
import com.netflix.spinnaker.halyard.config.model.v1.canary.newrelic.NewRelicCanaryServiceIntegration;
import com.netflix.spinnaker.halyard.config.model.v1.canary.prometheus.PrometheusCanaryAccount;
import com.netflix.spinnaker.halyard.config.model.v1.canary.prometheus.PrometheusCanaryServiceIntegration;
import com.netflix.spinnaker.halyard.config.model.v1.canary.signalfx.SignalfxCanaryAccount;
import com.netflix.spinnaker.halyard.config.model.v1.canary.signalfx.SignalfxCanaryServiceIntegration;
import com.netflix.spinnaker.halyard.config.model.v1.node.Node;
import com.netflix.spinnaker.halyard.config.model.v1.node.NodeIterator;
import com.netflix.spinnaker.halyard.config.model.v1.node.NodeIteratorFactory;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class Canary extends Node implements Cloneable {
  boolean enabled;
  List<? extends AbstractCanaryServiceIntegration> serviceIntegrations =
      Lists.newArrayList(
          new GoogleCanaryServiceIntegration(),
          new PrometheusCanaryServiceIntegration(),
          new DatadogCanaryServiceIntegration(),
          new SignalfxCanaryServiceIntegration(),
          new AwsCanaryServiceIntegration(),
          new NewRelicCanaryServiceIntegration());

  boolean reduxLoggerEnabled = true;
  String defaultMetricsAccount;
  String defaultStorageAccount;
  String defaultJudge = "NetflixACAJudge-v1.0";
  String defaultMetricsStore;
  boolean stagesEnabled = true;
  String atlasWebComponentsUrl;
  boolean templatesEnabled = true;
  boolean showAllConfigsEnabled = true;

  @Override
  public String getNodeName() {
    return "canary";
  }

  @Override
  public NodeIterator getChildren() {
    return NodeIteratorFactory.makeListIterator(
        serviceIntegrations.stream().map(m -> (Node) m).collect(Collectors.toList()));
  }

  public static Class<? extends AbstractCanaryAccount> translateCanaryAccountType(
      String serviceIntegrationName) {
    switch (serviceIntegrationName) {
      case GoogleCanaryServiceIntegration.NAME:
        return GoogleCanaryAccount.class;
      case PrometheusCanaryServiceIntegration.NAME:
        return PrometheusCanaryAccount.class;
      case DatadogCanaryServiceIntegration.NAME:
        return DatadogCanaryAccount.class;
      case SignalfxCanaryServiceIntegration.NAME:
        return SignalfxCanaryAccount.class;
      case AwsCanaryServiceIntegration.NAME:
        return AwsCanaryAccount.class;
      case NewRelicCanaryServiceIntegration.NAME:
        return NewRelicCanaryAccount.class;
      default:
        throw new IllegalArgumentException(
            "No account type for canary service integration " + serviceIntegrationName + ".");
    }
  }
}
