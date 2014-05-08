package com.netflix.oort.deployables

import com.netflix.oort.clusters.*
import com.netflix.oort.spring.ApplicationContextHolder
import org.springframework.beans.factory.annotation.Autowired

class Deployable {

  @Autowired
  private List<ClusterProvider> clusterProviders

  Deployable() {
    ApplicationContextHolder.applicationContext.autowireCapableBeanFactory.autowireBean(this)
  }

  String name
  String type
  Map<String, String> attributes

  private Clusters clusters

  Clusters getClusters() {
    if (!clusters) {
      Clusters clusters = new Clusters()
      for (provider in clusterProviders) {
        def providerClusters = provider.get(this.name)
        clusters.addAll providerClusters
      }
      this.clusters = clusters
    }
    clusters
  }

  static Deployable merge(Deployable a, Deployable b) {
    assert a.name == b.name
    def deployable = new Deployable(name: a.name, attributes: [:])
    if (a.attributes) {
      deployable.attributes << a.attributes
    }
    if (b.attributes) {
      deployable.attributes << b.attributes
    }
    deployable
  }
}