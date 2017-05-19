package com.netflix.spinnaker.halyard.config.model.v1.providers.dcos;

import lombok.Data;
import lombok.EqualsAndHashCode;

import com.netflix.spinnaker.halyard.config.model.v1.node.Cluster;
import com.netflix.spinnaker.halyard.config.model.v1.node.NodeIterator;
import com.netflix.spinnaker.halyard.config.model.v1.node.NodeIteratorFactory;
import com.netflix.spinnaker.halyard.config.model.v1.node.Validator;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemSetBuilder;

@Data
@EqualsAndHashCode(callSuper = false)
public class DCOSCluster extends Cluster {
  String name;
  String dcosUrl;
  String caCertData;
  Boolean insecureSkipTlsVerify;
  LoadBalancer loadBalancer;

  @Override
  public void accept(final ConfigProblemSetBuilder psBuilder, final Validator v) {
    v.validate(psBuilder, this);
  }

  @Override
  public String getNodeName() {
    return name;
  }

  @Override
  public NodeIterator getChildren() {
    return NodeIteratorFactory.makeEmptyIterator();
  }

  @Data
  public static class LoadBalancer {
    String image;
    String serviceAccountSecret;
  }
}

