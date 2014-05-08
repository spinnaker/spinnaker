package com.netflix.oort.deployables

public interface DeployableProvider {
  List<Deployable> list()
  Deployable get(String name)
}