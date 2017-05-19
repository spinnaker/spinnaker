package com.netflix.spinnaker.halyard.config.model.v1.providers.dcos;

import lombok.Data;

import com.netflix.spinnaker.halyard.config.model.v1.node.HasClustersProvider;
import com.netflix.spinnaker.halyard.config.model.v1.node.Node;
import com.netflix.spinnaker.halyard.config.model.v1.node.NodeIterator;
import com.netflix.spinnaker.halyard.config.model.v1.node.NodeIteratorFactory;
import com.netflix.spinnaker.halyard.config.model.v1.node.Validator;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemSetBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Data
public class DCOSProvider extends HasClustersProvider<DCOSAccount, DCOSCluster> {

  @Override
  public ProviderType providerType() {
    return ProviderType.DCOS;
  }

  @Override
  public void accept(ConfigProblemSetBuilder psBuilder, Validator v) {
    v.validate(psBuilder, this);
  }
}
