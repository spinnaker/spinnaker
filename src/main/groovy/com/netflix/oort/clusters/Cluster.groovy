package com.netflix.oort.clusters

public interface Cluster {
  String getName()
  String getZone()
  List<ServerGroup> getServerGroups()
}