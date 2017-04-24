/*
 * Copyright (c) 2017 Oracle America, Inc.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */
package com.netflix.spinnaker.clouddriver.oraclebmcs.deploy

import com.netflix.frigga.Names
import com.netflix.spinnaker.clouddriver.helpers.AbstractServerGroupNameResolver
import com.netflix.spinnaker.clouddriver.oraclebmcs.security.OracleBMCSNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.oraclebmcs.service.servergroup.OracleBMCSServerGroupService

class OracleBMCSServerGroupNameResolver extends AbstractServerGroupNameResolver {

  private static final String PHASE = "DEPLOY"

  private final OracleBMCSServerGroupService oracleBMCSServerGroupService
  private final OracleBMCSNamedAccountCredentials credentials
  final String region

  OracleBMCSServerGroupNameResolver(OracleBMCSServerGroupService service,
                                    OracleBMCSNamedAccountCredentials creds,
                                    String region) {
    this.oracleBMCSServerGroupService = service
    this.credentials = creds
    this.region = region
  }

  @Override
  String getPhase() {
    return PHASE
  }

  @Override
  List<AbstractServerGroupNameResolver.TakenSlot> getTakenSlots(String clusterName) {
    def serverGroupNames = oracleBMCSServerGroupService.listServerGroupNamesByClusterName(this.credentials, clusterName)

    return serverGroupNames.findResults { String serverGroupName ->
      def names = Names.parseName(serverGroupName)
      def serverGroup = oracleBMCSServerGroupService.getServerGroup(this.credentials, names.app, serverGroupName)
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
