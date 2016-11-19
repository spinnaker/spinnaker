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

import lombok.Data;

/**
 * A way to identify where a spot in your halconfig.
 */
@Data
public class NodeCoordinates implements Cloneable {
  String deployment = "";
  String provider = "";
  String webhook = "";
  String account = "";

  /**
   * This takes the input node, and updates the node coordinates to point to this node.
   *
   * @param node is the node to be pointed to.
   * @return A clone of the current coordinates, but pointing at the input node.
   */
  public NodeCoordinates refine(Node node) {
    NodeCoordinates result;
    try {
      result = (NodeCoordinates) this.clone();
    } catch (CloneNotSupportedException e) {
      throw new RuntimeException("Failed to clone node coordinates", e);
    }

    switch (node.getNodeType()) {
      case DEPLOYMENT:
        result.deployment = node.getNodeName();
        break;
      case PROVIDER:
        result.provider = node.getNodeName();
        break;
      case ACCOUNT:
        result.account = node.getNodeName();
        break;
      case WEBHOOK:
        result.webhook = node.getNodeName();
        break;
      case LIST:
      case ROOT:
        break;
      default:
        throw new RuntimeException("Unknown node type: " + node.getNodeType());
    }

    return result;
  }
}
