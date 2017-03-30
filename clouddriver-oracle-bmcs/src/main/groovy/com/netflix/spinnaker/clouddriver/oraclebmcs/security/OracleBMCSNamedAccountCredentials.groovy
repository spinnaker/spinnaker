/*
 * Copyright (c) 2017 Oracle America, Inc.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */
package com.netflix.spinnaker.clouddriver.oraclebmcs.security

import com.netflix.spinnaker.clouddriver.oraclebmcs.OracleBMCSCloudProvider
import com.netflix.spinnaker.clouddriver.security.AccountCredentials
import com.oracle.bmc.Region
import com.oracle.bmc.auth.AuthenticationDetailsProvider
import com.oracle.bmc.auth.ConfigFileAuthenticationDetailsProvider
import com.oracle.bmc.core.ComputeClient
import com.oracle.bmc.core.VirtualNetworkClient
import com.oracle.bmc.identity.IdentityClient
import com.oracle.bmc.identity.requests.ListAvailabilityDomainsRequest
import com.oracle.bmc.objectstorage.ObjectStorageClient

class OracleBMCSNamedAccountCredentials implements AccountCredentials<Object> {

  String cloudProvider = OracleBMCSCloudProvider.ID
  String name
  String environment
  String accountType
  String compartmentId
  List<String> requiredGroupMembership = []
  Object credentials
  String region
  List<OracleBMCSRegion> regions
  ComputeClient computeClient
  VirtualNetworkClient networkClient
  ObjectStorageClient objectStorageClient
  IdentityClient identityClient

  OracleBMCSNamedAccountCredentials(String name, String environment, String accountType, List<String> requiredGroupMembership, String compartmentId) {
    this.name = name
    this.environment = environment
    this.accountType = accountType
    this.requiredGroupMembership = requiredGroupMembership
    this.compartmentId = compartmentId

    AuthenticationDetailsProvider provider = new ConfigFileAuthenticationDetailsProvider(this.name)
    this.computeClient = new ComputeClient(provider)
    this.computeClient.setRegion(Region.US_PHOENIX_1)
    this.networkClient = new VirtualNetworkClient(provider)
    this.networkClient.setRegion(Region.US_PHOENIX_1)
    this.objectStorageClient = new ObjectStorageClient(provider)
    this.objectStorageClient.setRegion(Region.US_PHOENIX_1)
    this.identityClient = new IdentityClient(provider)
    this.identityClient.setRegion(Region.US_PHOENIX_1)
    this.region = Region.US_PHOENIX_1.regionId
    this.regions = [new OracleBMCSRegion(name: Region.US_PHOENIX_1.regionId,
      availabilityZones: this.identityClient.listAvailabilityDomains(ListAvailabilityDomainsRequest.builder()
        .compartmentId(this.compartmentId)
        .build()).items.collect { it.name })]
  }

  static class Builder {
    String name
    String environment
    String accountType
    List<String> requiredGroupMembership = []
    String compartmentId

    Builder name(String name) {
      this.name = name
      return this
    }

    Builder environment(String environment) {
      this.environment = environment
      return this
    }

    Builder accountType(String accountType) {
      this.accountType = accountType
      return this
    }

    Builder requiredGroupMembership(List<String> requiredGroupMembership) {
      this.requiredGroupMembership = requiredGroupMembership
      return this
    }

    Builder compartmentID(String compartmentID) {
      this.compartmentId = compartmentID
      return this
    }

    OracleBMCSNamedAccountCredentials build() {
      return new OracleBMCSNamedAccountCredentials(this.name, this.environment, this.accountType, this.requiredGroupMembership, this.compartmentId)
    }
  }

  class OracleBMCSRegion {
    String name
    List<String> availabilityZones
  }
}
