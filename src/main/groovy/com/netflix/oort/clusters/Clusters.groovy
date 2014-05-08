package com.netflix.oort.clusters

class Clusters {
  private final Map<String, List<Cluster>> clusters

  Clusters() {
    this([:])
  }

  Clusters(Map<String, List<Cluster>> clusters) {
    this.clusters = clusters
  }

  Cluster get(String clusterName) {
    clusters.get(clusterName)
  }

  List<Cluster> list() {
    clusters.values()?.flatten()
  }

  void add(Cluster cluster) {
    if (!clusters[cluster.name]) {
      clusters[cluster.name] = []
    }
    clusters[cluster.name] << cluster
  }

  void addAll(Collection<Cluster> clusters) {
    for (cluster in clusters) {
      add cluster
    }
  }

  void remove(String name) {
    clusters.remove(name)
  }
}
