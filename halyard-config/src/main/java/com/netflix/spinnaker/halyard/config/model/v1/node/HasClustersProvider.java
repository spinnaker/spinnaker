package com.netflix.spinnaker.halyard.config.model.v1.node;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public abstract class HasClustersProvider<A extends Account, C extends Cluster> extends Provider<A>
    implements Cloneable {
  private List<C> clusters = new ArrayList<>();

  @Override
  public NodeIterator getChildren() {
    NodeIterator parent = super.getChildren();
    final NodeIterator thisIterator =
        NodeIteratorFactory.makeListIterator(
            clusters.stream().map(a -> (Node) a).collect(Collectors.toList()));
    return NodeIteratorFactory.makeAppendNodeIterator(parent, thisIterator);
  }
}
