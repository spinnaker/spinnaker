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

package com.netflix.spinnaker.halyard.config.model.v1.providers;

import com.netflix.spinnaker.halyard.config.model.v1.Validator;
import com.netflix.spinnaker.halyard.config.model.v1.node.Node;
import com.netflix.spinnaker.halyard.config.model.v1.node.NodeIterator;
import com.netflix.spinnaker.halyard.config.model.v1.node.NodeIteratorFactory;
import com.netflix.spinnaker.halyard.config.model.v1.providers.dockerRegistry.DockerRegistryProvider;
import com.netflix.spinnaker.halyard.config.model.v1.providers.google.GoogleProvider;
import com.netflix.spinnaker.halyard.config.model.v1.providers.kubernetes.KubernetesProvider;
import lombok.Data;

@Data
public class Providers implements Cloneable, Node {
  KubernetesProvider kubernetes;
  DockerRegistryProvider dockerRegistry;
  GoogleProvider google;

  @Override
  public String getNodeName() {
    return "provider";
  }

  @Override
  public NodeIterator getIterator() {
    return NodeIteratorFactory.getReflectiveIterator(this);
  }

  @Override
  public NodeType getNodeType() {
    return NodeType.LIST;
  }

  @Override
  public void accept(Validator v) {
    v.validate(this);
  }
}
