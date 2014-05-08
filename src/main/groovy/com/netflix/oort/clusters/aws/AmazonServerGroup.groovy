package com.netflix.oort.clusters.aws

import com.netflix.oort.clusters.ServerGroup

class AmazonServerGroup extends HashMap implements ServerGroup {
  final String name

  AmazonServerGroup(String name, Map source) {
    super(source)
    this.name = name
  }

  int getInstanceCount() {
    (this.get("instances") as List).size()
  }
}
