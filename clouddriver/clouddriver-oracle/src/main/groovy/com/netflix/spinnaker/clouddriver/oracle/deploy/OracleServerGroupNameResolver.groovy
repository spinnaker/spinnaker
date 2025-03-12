/*
 * Copyright (c) 2017 Oracle America, Inc.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */
package com.netflix.spinnaker.clouddriver.oracle.deploy

import com.netflix.frigga.Names
import com.netflix.spinnaker.clouddriver.helpers.AbstractServerGroupNameResolver
import com.netflix.spinnaker.clouddriver.oracle.security.OracleNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.oracle.service.servergroup.OracleServerGroupService

class OracleServerGroupNameResolver extends AbstractServerGroupNameResolver {

  private static final String PHASE = "DEPLOY"

  private final OracleServerGroupService oracleServerGroupService
  private final OracleNamedAccountCredentials credentials
  final String region

  OracleServerGroupNameResolver(OracleServerGroupService service,
                                    OracleNamedAccountCredentials creds,
                                    String region) {
    this.oracleServerGroupService = service
    this.credentials = creds
    this.region = region
  }

  @Override
  String getPhase() {
    return PHASE
  }

  @Override
  List<AbstractServerGroupNameResolver.TakenSlot> getTakenSlots(String clusterName) {
    def serverGroupNames = oracleServerGroupService.listServerGroupNamesByClusterName(this.credentials, clusterName)

    return serverGroupNames.findResults { String serverGroupName ->
      def names = Names.parseName(serverGroupName)
      def serverGroup = oracleServerGroupService.getServerGroup(this.credentials, names.app, serverGroupName)
      if (names.cluster == clusterName) {
        return new AbstractServerGroupNameResolver.TakenSlot(
          serverGroupName: serverGroupName,
          sequence: names.sequence,
          createdTime: new Date(serverGroup.getView().getCreatedTime())
        )
      } else {
        return null
      }
    }
  }
}
