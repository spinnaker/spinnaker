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

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.netflix.spinnaker.halyard.config.model.v1.canary.aws.AwsCanaryServiceIntegration;
import com.netflix.spinnaker.halyard.config.model.v1.canary.datadog.DatadogCanaryServiceIntegration;
import com.netflix.spinnaker.halyard.config.model.v1.canary.google.GoogleCanaryServiceIntegration;
import com.netflix.spinnaker.halyard.config.model.v1.canary.newrelic.NewRelicCanaryServiceIntegration;
import com.netflix.spinnaker.halyard.config.model.v1.canary.prometheus.PrometheusCanaryServiceIntegration;
import com.netflix.spinnaker.halyard.config.model.v1.canary.signalfx.SignalfxCanaryServiceIntegration;
import com.netflix.spinnaker.halyard.config.model.v1.node.Node;
import com.netflix.spinnaker.halyard.config.model.v1.node.NodeIterator;
import com.netflix.spinnaker.halyard.config.model.v1.node.NodeIteratorFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Data;
import lombok.EqualsAndHashCode;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "name")
@JsonSubTypes({
  @JsonSubTypes.Type(
      value = GoogleCanaryServiceIntegration.class,
      name = GoogleCanaryServiceIntegration.NAME),
  @JsonSubTypes.Type(
      value = PrometheusCanaryServiceIntegration.class,
      name = PrometheusCanaryServiceIntegration.NAME),
  @JsonSubTypes.Type(
      value = DatadogCanaryServiceIntegration.class,
      name = DatadogCanaryServiceIntegration.NAME),
  @JsonSubTypes.Type(
      value = SignalfxCanaryServiceIntegration.class,
      name = SignalfxCanaryServiceIntegration.NAME),
  @JsonSubTypes.Type(
      value = AwsCanaryServiceIntegration.class,
      name = AwsCanaryServiceIntegration.NAME),
  @JsonSubTypes.Type(
      value = NewRelicCanaryServiceIntegration.class,
      name = NewRelicCanaryServiceIntegration.NAME)
})
@Data
@EqualsAndHashCode(callSuper = false)
public abstract class AbstractCanaryServiceIntegration<A extends AbstractCanaryAccount> extends Node
    implements Cloneable {
  boolean enabled;
  List<A> accounts = new ArrayList<>();

  public abstract String getName();

  @Override
  public String getNodeName() {
    return getName();
  }

  @Override
  public NodeIterator getChildren() {
    return NodeIteratorFactory.makeListIterator(
        accounts.stream().map(a -> (Node) a).collect(Collectors.toList()));
  }

  public static enum SupportedTypes {
    METRICS_STORE,
    CONFIGURATION_STORE,
    OBJECT_STORE
  }
}
