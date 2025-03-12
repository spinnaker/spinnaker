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
 *
 *
 */

package com.netflix.spinnaker.halyard.config.model.v1.ha;

import com.netflix.spinnaker.halyard.config.model.v1.node.Node;
import com.netflix.spinnaker.halyard.config.model.v1.node.NodeIterator;
import com.netflix.spinnaker.halyard.config.model.v1.node.NodeIteratorFactory;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Data
@EqualsAndHashCode(callSuper = false)
public abstract class HaService extends Node implements Cloneable {
  boolean enabled = false;

  @Override
  public NodeIterator getChildren() {
    return NodeIteratorFactory.makeEmptyIterator();
  }

  @Override
  public String getNodeName() {
    return haServiceType().getName();
  }

  public abstract HaServiceType haServiceType();

  public enum HaServiceType {
    CLOUDDRIVER("clouddriver"),
    ECHO("echo");

    @Getter final String name;

    HaServiceType(String name) {
      this.name = name;
    }
  }
}
