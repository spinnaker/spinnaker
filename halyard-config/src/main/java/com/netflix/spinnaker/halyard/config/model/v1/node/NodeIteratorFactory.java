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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

/**
 * @see Node
 * @see NodeIterator
 */
@Slf4j
public class NodeIteratorFactory {
  /**
   * Creates an iterator from a Node that allows us to iterate over all sub-fields with type node.
   *
   * @param node the node who's fields to iterate over.
   * @return the resulting interator.
   */
  public static NodeIterator makeReflectiveIterator(Node node) {
    List<Node> nodes =
        new ArrayList<>(Arrays.asList(node.getClass().getDeclaredFields()))
            .stream()
                .filter(
                    f -> {
                      try {
                        f.setAccessible(true);
                        return f.get(node) instanceof Node;
                      } catch (IllegalAccessException e) {
                        log.warn("Could not retrieve field value for " + f.getName(), e);
                        return false;
                      } finally {
                        f.setAccessible(false);
                      }
                    })
                .map(
                    n -> {
                      try {
                        n.setAccessible(true);
                        return (Node) n.get(node);
                      } catch (IllegalAccessException | SecurityException e) {
                        log.warn("Could not retrieve node value for " + n.getName(), e);
                        return null;
                      } finally {
                        n.setAccessible(false);
                      }
                    })
                .filter(n -> n != null)
                .collect(Collectors.toList());

    log.trace(
        "Node " + node.getNodeName() + " reflectively collected " + nodes.size() + " children");

    return new NodeListIterator(nodes);
  }

  public static NodeIterator makeListIterator(List<Node> nodes) {
    return new NodeListIterator(nodes);
  }

  public static NodeIterator makeSingletonIterator(Node node) {
    List<Node> nodes = new ArrayList<>();
    nodes.add(node);
    return new NodeListIterator(nodes);
  }

  public static NodeIterator makeEmptyIterator() {
    return new NodeEmptyIterator();
  }

  public static NodeIterator makeAppendNodeIterator(NodeIterator a, NodeIterator b) {
    return new AppendNodeIterator(a, b);
  }

  private static class NodeEmptyIterator implements NodeIterator {
    @Override
    public Node getNext(NodeFilter filter) {
      return null;
    }

    @Override
    public Node getNext() {
      return null;
    }
  }

  private static class AppendNodeIterator implements NodeIterator {
    NodeIterator a;
    NodeIterator b;

    AppendNodeIterator(NodeIterator a, NodeIterator b) {
      this.a = a;
      this.b = b;
    }

    @Override
    public Node getNext(NodeFilter filter) {
      Node aNext = a.getNext(filter);
      return aNext != null ? aNext : b.getNext(filter);
    }

    @Override
    public Node getNext() {
      Node aNext = a.getNext();
      return aNext != null ? aNext : b.getNext();
    }
  }

  private static class NodeListIterator implements NodeIterator {
    List<Node> nodes = new ArrayList<>();
    Integer index = 0;

    NodeListIterator(List<Node> nodes) {
      this.nodes = nodes;
    }

    @Override
    public Node getNext() {
      Node result = null;
      if (hasNext()) {
        result = nodes.get(index);
        index++;
      }
      return result;
    }

    @Override
    public Node getNext(NodeFilter filter) {
      while (hasNext()) {
        Node result = getNext();
        if (result.matchesToRoot(filter)) {
          return result;
        }
      }

      return null;
    }

    private boolean hasNext() {
      return index < nodes.size();
    }
  }
}
