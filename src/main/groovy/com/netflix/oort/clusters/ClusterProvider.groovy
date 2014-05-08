package com.netflix.oort.clusters

interface ClusterProvider {
  Map<String, List<Cluster>> get(String deployable)
  List<Cluster> getByName(String deployable, String clusterName)
  Cluster getByNameAndZone(String deployable, String clusterName, String zoneName)
}
