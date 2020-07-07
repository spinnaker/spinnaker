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

package com.netflix.spinnaker.halyard.config.model.v1.node;

import com.netflix.spinnaker.fiat.model.resources.Permissions;
import com.netflix.spinnaker.halyard.config.config.v1.ArtifactSourcesConfig;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public abstract class Account extends Node implements Cloneable {
  String name;
  String environment;
  List<String> requiredGroupMembership = new ArrayList<>();
  Permissions.Builder permissions = new Permissions.Builder();

  @Override
  public String getNodeName() {
    return name;
  }

  // Override this method if your cloud provider account needs special settings enabled for it act
  // as a bootstrapping account.
  public void makeBootstrappingAccount(ArtifactSourcesConfig artifactSourcesConfig) {
    permissions.clear();
    requiredGroupMembership.clear();
  }

  @Override
  public NodeIterator getChildren() {
    return NodeIteratorFactory.makeEmptyIterator();
  }
}
