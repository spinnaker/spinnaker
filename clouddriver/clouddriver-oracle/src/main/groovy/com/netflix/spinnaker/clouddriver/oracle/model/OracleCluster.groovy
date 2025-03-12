/*
 * Copyright (c) 2017 Oracle America, Inc.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */
package com.netflix.spinnaker.clouddriver.oracle.model

import com.fasterxml.jackson.annotation.JsonIgnore
import com.netflix.spinnaker.clouddriver.model.Cluster
import com.netflix.spinnaker.clouddriver.model.LoadBalancer
import com.netflix.spinnaker.clouddriver.oracle.OracleCloudProvider
import groovy.transform.Canonical
import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode

@CompileStatic
@EqualsAndHashCode(includes = ["name", "accountName"])
class OracleCluster {

  String name
  String accountName
  Set<OracleServerGroup> serverGroups

  @JsonIgnore
  View getView() {
    new View(this)
  }

  @Canonical
  class View implements Cluster {

    final String type = OracleCloudProvider.ID
    final String name
    final String accountName
    final Set<OracleServerGroup.View> serverGroups
    final Set<LoadBalancer> loadBalancers

    View(OracleCluster oracleCluster){
      name = oracleCluster.name
      accountName = oracleCluster.accountName
      serverGroups = oracleCluster.serverGroups.collect { OracleServerGroup it -> it.getView() } as Set
      loadBalancers = [] as Set
    }

  }
}
