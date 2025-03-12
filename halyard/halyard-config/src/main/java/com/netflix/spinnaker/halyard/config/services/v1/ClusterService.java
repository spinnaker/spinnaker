package com.netflix.spinnaker.halyard.config.services.v1;

import com.netflix.spinnaker.halyard.config.error.v1.ConfigNotFoundException;
import com.netflix.spinnaker.halyard.config.error.v1.IllegalConfigException;
import com.netflix.spinnaker.halyard.config.model.v1.node.Cluster;
import com.netflix.spinnaker.halyard.config.model.v1.node.HasClustersProvider;
import com.netflix.spinnaker.halyard.config.model.v1.node.NodeFilter;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemBuilder;
import com.netflix.spinnaker.halyard.core.error.v1.HalException;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem;
import com.netflix.spinnaker.halyard.core.problem.v1.ProblemSet;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** */
@Component
public class ClusterService {
  @Autowired private LookupService lookupService;

  @Autowired private ProviderService providerService;

  @Autowired private ValidateService validateService;

  @Autowired private OptionsService optionsService;

  public List<Cluster> getAllClusters(String deploymentName, String providerName) {
    NodeFilter filter =
        new NodeFilter().setDeployment(deploymentName).setProvider(providerName).withAnyCluster();

    List<Cluster> matchingClusters = lookupService.getMatchingNodesOfType(filter, Cluster.class);

    if (matchingClusters.size() == 0) {
      throw new ConfigNotFoundException(
          new ConfigProblemBuilder(Problem.Severity.FATAL, "No clusters could be found").build());
    } else {
      return matchingClusters;
    }
  }

  private Cluster getCluster(NodeFilter filter, String clusterName) {
    List<Cluster> matchingClusters = lookupService.getMatchingNodesOfType(filter, Cluster.class);

    switch (matchingClusters.size()) {
      case 0:
        throw new ConfigNotFoundException(
            new ConfigProblemBuilder(
                    Problem.Severity.FATAL,
                    "No cluster with name \"" + clusterName + "\" was found")
                .setRemediation(
                    "Check if this cluster was defined in another provider, or create a new one")
                .build());
      case 1:
        return matchingClusters.get(0);
      default:
        throw new IllegalConfigException(
            new ConfigProblemBuilder(
                    Problem.Severity.FATAL,
                    "More than one cluster named \"" + clusterName + "\" was found")
                .setRemediation(
                    "Manually delete/rename duplicate clusters with name \""
                        + clusterName
                        + "\" in your halconfig file")
                .build());
    }
  }

  public Cluster getProviderCluster(
      String deploymentName, String providerName, String clusterName) {
    NodeFilter filter =
        new NodeFilter()
            .setDeployment(deploymentName)
            .setProvider(providerName)
            .setCluster(clusterName);
    return getCluster(filter, clusterName);
  }

  public Cluster getAnyProviderCluster(String deploymentName, String clusterName) {
    NodeFilter filter =
        new NodeFilter().setDeployment(deploymentName).withAnyProvider().setCluster(clusterName);
    return getCluster(filter, clusterName);
  }

  public void setCluster(
      String deploymentName, String providerName, String clusterName, Cluster newCluster) {
    final HasClustersProvider clustersProvider =
        providerService.getHasClustersProvider(deploymentName, providerName);

    for (int i = 0; i < clustersProvider.getClusters().size(); i++) {
      Cluster cluster = (Cluster) clustersProvider.getClusters().get(i);
      if (cluster.getNodeName().equals(clusterName)) {
        clustersProvider.getClusters().set(i, newCluster);
        return;
      }
    }

    throw new HalException(Problem.Severity.FATAL, "Cluster \"" + clusterName + "\" wasn't found");
  }

  public void deleteCluster(String deploymentName, String providerName, String clusterName) {
    final HasClustersProvider clustersProvider =
        providerService.getHasClustersProvider(deploymentName, providerName);

    final List<Cluster> clusters = (List<Cluster>) clustersProvider.getClusters();
    boolean removed = clusters.removeIf(cluster -> cluster.getName().equals(clusterName));

    if (!removed) {
      throw new HalException(
          Problem.Severity.FATAL, "Cluster \"" + clusterName + "\" wasn't found");
    }
  }

  public void addCluster(String deploymentName, String providerName, Cluster newCluster) {
    final HasClustersProvider clustersProvider =
        providerService.getHasClustersProvider(deploymentName, providerName);

    clustersProvider.getClusters().add(newCluster);
  }

  public ProblemSet validateCluster(
      String deploymentName, String providerName, String clusterName) {
    NodeFilter filter =
        new NodeFilter()
            .setDeployment(deploymentName)
            .setProvider(providerName)
            .setCluster(clusterName);
    return validateService.validateMatchingFilter(filter);
  }

  public ProblemSet validateAllClusters(String deploymentName, String providerName) {
    NodeFilter filter =
        new NodeFilter().setDeployment(deploymentName).setProvider(providerName).withAnyCluster();
    return validateService.validateMatchingFilter(filter);
  }
}
