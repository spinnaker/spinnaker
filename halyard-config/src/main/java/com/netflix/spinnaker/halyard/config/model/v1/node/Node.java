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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.introspect.WithMember;
import com.netflix.spinnaker.halyard.config.model.v1.Validatable;

/**
 * The "Node" class represents effectively a YAML node in our config hierarchy that can be validated. It also provides
 * the name of the node with a small tweak:
 *
 * If a yaml node is an element of a list, we use that nodes "name" field to represent name.
 *
 * The motivation for this is to allow us to navigate YAML paths in our halconfig, and validate each node (if necessary)
 * along the way.
 */
public interface Node extends Validatable {
  @JsonIgnore
  public String getNodeName();

  @JsonIgnore
  public NodeIterator getIterator();

  @JsonIgnore
  public NodeType getNodeType();

  enum NodeType {
    DEPLOYMENT,
    PROVIDER,
    ACCOUNT,
    WEBHOOK,
    LIST,
    ROOT,
  }
}
