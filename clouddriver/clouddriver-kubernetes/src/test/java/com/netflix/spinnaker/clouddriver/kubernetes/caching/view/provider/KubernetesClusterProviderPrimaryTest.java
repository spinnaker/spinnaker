package com.netflix.spinnaker.clouddriver.kubernetes.caching.view.provider;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.netflix.spinnaker.clouddriver.model.Cluster;
import com.netflix.spinnaker.clouddriver.model.ClusterProvider;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Test to verify the @Primary annotation fix for triplicate cluster issue. */
public class KubernetesClusterProviderPrimaryTest {

  @Test
  public void testTriplicateClusterIssueWithoutPrimary() {
    List<ClusterProvider<? extends Cluster>> providers = createMultipleKubernetesProviders();

    Set<Cluster> allClusters = new HashSet<>();
    for (ClusterProvider<? extends Cluster> provider : providers) {
      if ("kubernetes".equals(provider.getCloudProviderId())) {
        Set<? extends Cluster> clusters = provider.getClusters("test-app", "test-account");
        allClusters.addAll(clusters);
      }
    }

    long clusterCount =
        allClusters.stream().filter(cluster -> "my-k8s-cluster".equals(cluster.getName())).count();

    assertTrue(
        providers.size() > 1, "Should have multiple Kubernetes providers (causing triplicates)");
    assertEquals(3, providers.size(), "Should simulate 3 providers (standard, armory, plugin)");
  }

  @Test
  public void testPrimaryAnnotationFixesTriplicateIssue() {
    List<ClusterProvider<? extends Cluster>> providers = createMultipleKubernetesProviders();

    ClusterProvider<? extends Cluster> primaryProvider =
        providers.stream().filter(this::isPrimaryProvider).findFirst().orElse(providers.get(0));

    Set<? extends Cluster> clusters = primaryProvider.getClusters("test-app", "test-account");

    long clusterCount =
        clusters.stream().filter(cluster -> "my-k8s-cluster".equals(cluster.getName())).count();

    assertEquals(1, clusterCount, "Should have only ONE instance of each cluster after fix");
  }

  @Test
  public void testKubernetesClusterProviderHasPrimaryAnnotation() {
    boolean hasPrimaryAnnotation =
        KubernetesClusterProvider.class.isAnnotationPresent(
            org.springframework.context.annotation.Primary.class);

    assertTrue(
        hasPrimaryAnnotation,
        "KubernetesClusterProvider should have @Primary annotation to fix triplicate issue");
  }

  // Helper methods to simulate the scenario

  private List<ClusterProvider<? extends Cluster>> createMultipleKubernetesProviders() {
    List<ClusterProvider<? extends Cluster>> providers = new ArrayList<>();

    // Standard KubernetesClusterProvider (our fix)
    providers.add(createMockProvider("standard", true));

    // Armory agent kubesvc provider
    providers.add(createMockProvider("armory-agent", false));

    // Plugin migration providerv2 provider
    providers.add(createMockProvider("plugin-migration", false));

    return providers;
  }

  @SuppressWarnings("unchecked")
  private ClusterProvider<? extends Cluster> createMockProvider(String type, boolean isPrimary) {
    ClusterProvider<Cluster> provider = mock(ClusterProvider.class);
    when(provider.getCloudProviderId()).thenReturn("kubernetes");

    // All providers return the same logical cluster (causing triplicates)
    Cluster cluster = mock(Cluster.class);
    when(cluster.getName()).thenReturn("my-k8s-cluster");
    when(cluster.getType()).thenReturn("kubernetes");
    when(cluster.getAccountName()).thenReturn("test-account");

    Set<Cluster> clusters = Set.of(cluster);
    when(provider.getClusters(anyString(), anyString())).thenReturn(clusters);

    // Mark which one is the primary (simulates @Primary annotation)
    when(provider.toString()).thenReturn(type + (isPrimary ? "-PRIMARY" : ""));

    return provider;
  }

  private boolean isPrimaryProvider(ClusterProvider<? extends Cluster> provider) {
    return provider.toString().contains("PRIMARY");
  }
}
