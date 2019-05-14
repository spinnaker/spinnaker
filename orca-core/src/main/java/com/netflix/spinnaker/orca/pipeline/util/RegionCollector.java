package com.netflix.spinnaker.orca.pipeline.util;

import com.netflix.spinnaker.orca.clouddriver.utils.CloudProviderAware;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import java.util.*;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

@Component
/**
 * Collects relevant regions from stages
 *
 * <p>Used by e.g. FindImageFromClusterTask and BakeTask
 */
public class RegionCollector {
  /**
   * Traverses all descendant stages of a given stage looking for deploy/deploy canary stages When
   * found, extracts the regions those stages deploy to.
   *
   * @param stage Stage for which to traverse the regions for
   * @return union of all regions or an empty set
   */
  public @NotNull Set<String> getRegionsFromChildStages(Stage stage) {
    Set<String> deployRegions = new HashSet<>();
    Map<String, Object> context = stage.getContext();
    String stageCloudProviderType =
        (String)
            context.getOrDefault(
                "cloudProviderType",
                context.getOrDefault("cloudProvider", CloudProviderAware.DEFAULT_CLOUD_PROVIDER));

    List<Stage> childStages = stage.allDownstreamStages();

    childStages.stream()
        .filter(it -> "deploy".equals(it.getType()))
        .forEach(
            deployStage -> {
              List<Map> clusters =
                  (List<Map>) deployStage.getContext().getOrDefault("clusters", new ArrayList<>());
              for (Map cluster : clusters) {
                if (stageCloudProviderType.equals(cluster.get("cloudProvider"))) {
                  deployRegions.addAll(
                      ((Map) cluster.getOrDefault("availabilityZones", new HashMap())).keySet());
                }
              }

              Map clusterMap =
                  (Map) deployStage.getContext().getOrDefault("cluster", new HashMap<>());
              if (stageCloudProviderType.equals(clusterMap.get("cloudProvider"))) {
                deployRegions.addAll(
                    ((Map) clusterMap.getOrDefault("availabilityZones", new HashMap())).keySet());
              }
            });

    // TODO(duftler): Also filter added canary regions once canary supports multiple platforms.
    childStages.stream()
        .filter(it -> "canary".equals(it.getType()))
        .forEach(
            canaryStage -> {
              List<Map> clusterPairs =
                  (List<Map>)
                      canaryStage.getContext().getOrDefault("clusterPairs", new ArrayList<>());

              for (Map clusterPair : clusterPairs) {
                Map baseline = ((Map) clusterPair.getOrDefault("baseline", new HashMap()));
                Map availabilityZones =
                    ((Map) baseline.getOrDefault("availabilityZones", new HashMap()));
                deployRegions.addAll(availabilityZones.keySet());

                Map canary = ((Map) clusterPair.getOrDefault("canary", new HashMap()));
                availabilityZones = ((Map) canary.getOrDefault("availabilityZones", new HashMap()));
                deployRegions.addAll(availabilityZones.keySet());
              }
            });

    return deployRegions;
  }
}
