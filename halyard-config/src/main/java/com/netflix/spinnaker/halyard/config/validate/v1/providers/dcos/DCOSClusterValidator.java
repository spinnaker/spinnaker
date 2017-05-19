package com.netflix.spinnaker.halyard.config.validate.v1.providers.dcos;

import com.google.common.base.Strings;
import com.netflix.spinnaker.halyard.config.model.v1.node.Validator;
import com.netflix.spinnaker.halyard.config.model.v1.providers.dcos.DCOSCluster;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemSetBuilder;

import org.springframework.stereotype.Component;

import static com.netflix.spinnaker.halyard.core.problem.v1.Problem.Severity.ERROR;
import static com.netflix.spinnaker.halyard.core.problem.v1.Problem.Severity.WARNING;

/**
 * TODO: use clouddriver components for full validation
 */
@Component
public class DCOSClusterValidator extends Validator<DCOSCluster> {
  @Override
  public void validate(final ConfigProblemSetBuilder problems, final DCOSCluster cluster) {
    if (Strings.isNullOrEmpty(cluster.getDcosUrl())) {
      problems.addProblem(ERROR, "Cluster must have a URL");
    }

    final DCOSCluster.LoadBalancer loadBalancer = cluster.getLoadBalancer();
    if (loadBalancer == null || Strings.isNullOrEmpty(loadBalancer.getImage())) {
      problems.addProblem(WARNING,
          "Load balancer pipeline stages will not be able to be used unless a marathon-lb image is specified");
    }
  }
}
