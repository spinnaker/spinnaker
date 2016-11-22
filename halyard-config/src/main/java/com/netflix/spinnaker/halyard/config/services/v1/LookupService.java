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

package com.netflix.spinnaker.halyard.config.services.v1;

import com.netflix.spinnaker.halyard.config.config.v1.HalconfigParser;
import com.netflix.spinnaker.halyard.config.model.v1.node.Halconfig;
import com.netflix.spinnaker.halyard.config.model.v1.node.Node;
import com.netflix.spinnaker.halyard.config.model.v1.node.NodeFilter;
import com.netflix.spinnaker.halyard.config.model.v1.node.NodeIterator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
public class LookupService {
  @Autowired
  HalconfigParser parser;

  /**
   * Given a node filter and a node type, find all nodes that match both the filter and the type of the Node.
   * @param filter is the filter to lookup by.
   * @param clazz is the class of the node type we want to find.
   * @return
   */
  public List<Node> getMatchingNodesOfType(NodeFilter filter, Class<? extends Node> clazz) {
    Halconfig halconfig = parser.getConfig();

    return getMatchingLeafNodes(halconfig, filter)
        .stream()
        .filter(clazz::isInstance)
        .collect(Collectors.toList());
  }

  /**
   * If the filter represents a pruned tree, then this
   * @param node is the node whos children we want to find.
   * @param filter is the filter to lookup by.
   * @return
   */
  private List<Node> getMatchingLeafNodes(Node node, NodeFilter filter) {
    log.trace("Checking for leaf nodes of node " + node.getNodeName());

    List<Node> result = new ArrayList<>();

    NodeIterator children = node.getChildren();

    Node recurse = children.getNext(filter);
    while (recurse != null) {
      result.addAll(getMatchingLeafNodes(recurse, filter));
      recurse = children.getNext(filter);
    }

    // If no leaves were collected, then we must be a leaf.
    //
    // We can infer this because this function never returns an empty list, so for result to be empty we could
    // not have iterated over anything.
    if (result.isEmpty()) {
      log.trace("Node " + node.getNodeName() + " was deemed to be a leaf");
      result.add(node);
    }

    return result;
  }
}
