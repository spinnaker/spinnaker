package com.netflix.oort.clusters.aws

import com.fasterxml.jackson.annotation.JsonIgnore
import com.netflix.oort.clusters.Cluster
import com.netflix.oort.clusters.ServerGroup
import com.netflix.oort.remoting.AggregateRemoteResource
import org.springframework.beans.factory.annotation.Autowired

class AmazonCluster implements Cluster {
  @Autowired
  @JsonIgnore
  AggregateRemoteResource edda

  String name
  String zone
  List<ServerGroup> serverGroups
}
