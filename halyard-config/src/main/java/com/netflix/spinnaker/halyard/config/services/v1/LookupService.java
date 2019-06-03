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
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class LookupService {
  @Autowired HalconfigParser parser;

  /**
   * Given a node filter and a node type, find all nodes that match both the filter and the type of
   * the Node.
   *
   * @param filter is the filter to lookup by.
   * @param clazz is the class of the node type we want to find.
   * @return the nodes matching the filter and clazz.
   */
  public <T extends Node> List<T> getMatchingNodesOfType(NodeFilter filter, Class<T> clazz) {
    Halconfig halconfig = parser.getHalconfig();

    return getMatchingNodes(halconfig, filter).stream()
        .filter(clazz::isInstance)
        .map(n -> (T) n)
        .collect(Collectors.toList());
  }

  /**
   * @param node is the node whose children we want to find.
   * @param filter is the filter to lookup by.
   * @return the children of the input node matching the filter.
   */
  private List<Node> getMatchingNodes(Node node, NodeFilter filter) {
    log.trace("Checking for leaf nodes of node " + node.getNodeName());

    List<Node> result = new ArrayList<>();

    NodeIterator children = node.getChildren();

    Node recurse = children.getNext(filter);
    while (recurse != null) {
      result.addAll(getMatchingNodes(recurse, filter));
      recurse = children.getNext(filter);
    }

    // If we have visited this node, it must have matched the filter.
    result.add(node);

    return result;
  }

  public <T extends Node> T getSingularNodeOrDefault(
      NodeFilter filter, Class<T> clazz, Supplier<T> defaultNode, Consumer<T> setDefault) {
    List<T> matching = getMatchingNodesOfType(filter, clazz);

    switch (matching.size()) {
      case 0:
        T node = defaultNode.get();
        setDefault.accept(node);
        return node;
      case 1:
        return matching.get(0);
      default:
        throw new RuntimeException(
            "It shouldn't be possible to have multiple "
                + clazz.getCanonicalName()
                + " nodes. This is a bug.");
    }
  }
}
