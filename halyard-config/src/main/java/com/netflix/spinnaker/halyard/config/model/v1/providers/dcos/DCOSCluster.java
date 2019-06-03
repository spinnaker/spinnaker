package com.netflix.spinnaker.halyard.config.model.v1.providers.dcos;

import com.netflix.spinnaker.halyard.config.model.v1.node.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class DCOSCluster extends Cluster {
  String name;
  String dcosUrl;
  @LocalFile @SecretFile String caCertFile;
  Boolean insecureSkipTlsVerify;
  LoadBalancer loadBalancer;

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
    @Secret String serviceAccountSecret;
  }
}
