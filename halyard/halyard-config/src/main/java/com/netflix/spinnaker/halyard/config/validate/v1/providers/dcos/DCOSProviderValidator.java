package com.netflix.spinnaker.halyard.config.validate.v1.providers.dcos;

import com.netflix.spinnaker.halyard.config.model.v1.node.Validator;
import com.netflix.spinnaker.halyard.config.model.v1.providers.dcos.DCOSCluster;
import com.netflix.spinnaker.halyard.config.model.v1.providers.dcos.DCOSProvider;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemSetBuilder;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem;
import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Component;

/** */
@Component
public class DCOSProviderValidator extends Validator<DCOSProvider> {
  @Override
  public void validate(final ConfigProblemSetBuilder p, final DCOSProvider provider) {
    Set<String> clusters = new HashSet<>();

    for (DCOSCluster cluster : provider.getClusters()) {
      if (clusters.contains(cluster.getName())) {
        p.addProblem(
                Problem.Severity.FATAL,
                "Account \"" + cluster.getName() + "\" appears more than once")
            .setRemediation("Change the name of the cluster in " + provider.getNodeName());
      } else {
        clusters.add(cluster.getName());
      }
    }
  }
}
