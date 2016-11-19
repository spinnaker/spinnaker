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

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @see Node
 * @see NodeIterator
 */
@Slf4j
public class NodeIteratorFactory {
  /**
   *
   * Transforms a Node into an iterator that allows us to iterate over all sub-fields with type node.
   *
   * @param node the node who's fields to iterate over.
   * @return the resulting interator.
   */
  public static NodeIterator getReflectiveIterator(Node node) {
    List<Node> nodes = new ArrayList<>(Arrays.asList(node.getClass().getFields()))
        .stream()
        .filter(f -> f.getType() == Node.class)
        .map(n -> {
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
        .filter(n -> n != null).collect(Collectors.toList());

    return new NodeListIterator(nodes);
  }

  public static NodeIterator getListIterator(List<Node> nodes) {
    return new NodeListIterator(nodes);
  }

  public static NodeIterator getEmptyIterator() {
    return new NodeEmptyIterator();
  }

  private static class NodeEmptyIterator implements NodeIterator {
    private Node getNext() {
      return null;
    }

    @Override
    public Node getNext(NodeFilter filter) {
      return null;
    }

    @Override
    public boolean hasNext() {
      return false;
    }
  }

  private static class NodeListIterator implements NodeIterator {
    List<Node> nodes = new ArrayList<>();
    Integer index = 0;

    NodeListIterator(List<Node> nodes) {
      this.nodes = nodes;
    }

    private Node getNext() {
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
        if (filter.matches(result)) {
          return result;
        }
      }

      return null;
    }

    @Override
    public boolean hasNext() {
      return index < nodes.size();
    }
  }
}
