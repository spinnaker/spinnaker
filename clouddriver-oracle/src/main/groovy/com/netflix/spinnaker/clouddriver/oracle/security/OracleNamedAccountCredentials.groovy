/*
 * Copyright (c) 2017 Oracle America, Inc.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */
package com.netflix.spinnaker.clouddriver.oracle.security

import com.google.common.base.Supplier
import com.netflix.spinnaker.clouddriver.oracle.OracleCloudProvider
import com.netflix.spinnaker.clouddriver.security.AccountCredentials
import com.oracle.bmc.Region
import com.oracle.bmc.auth.AuthenticationDetailsProvider
import com.oracle.bmc.auth.SimpleAuthenticationDetailsProvider
import com.oracle.bmc.auth.SimplePrivateKeySupplier
import com.oracle.bmc.core.ComputeClient
import com.oracle.bmc.core.VirtualNetworkClient
import com.oracle.bmc.identity.IdentityClient
import com.oracle.bmc.identity.requests.ListAvailabilityDomainsRequest
import com.oracle.bmc.loadbalancer.LoadBalancerClient
import com.oracle.bmc.objectstorage.ObjectStorageClient

class OracleNamedAccountCredentials implements AccountCredentials<Object> {

  String cloudProvider = OracleCloudProvider.ID
  String name
  String environment
  String accountType
  String compartmentId
  String userId
  String fingerprint
  String sshPrivateKeyFilePath
  String privateKeyPassphrase
  String tenancyId
  String region
  List<String> requiredGroupMembership = []
  Object credentials
  List<OracleRegion> regions
  ComputeClient computeClient
  VirtualNetworkClient networkClient
  ObjectStorageClient objectStorageClient
  IdentityClient identityClient
  LoadBalancerClient loadBalancerClient

  OracleNamedAccountCredentials(String name,
                                    String environment,
                                    String accountType,
                                    List<String> requiredGroupMembership,
                                    String compartmentId,
                                    String userId,
                                    String fingerprint,
                                    String sshPrivateKeyFilePath,
                                    String privateKeyPassphrase,
                                    String tenancyId,
                                    String region) {
    this.name = name
    this.environment = environment
    this.accountType = accountType
    this.requiredGroupMembership = requiredGroupMembership
    this.compartmentId = compartmentId
    this.userId = userId
    this.fingerprint = fingerprint
    this.sshPrivateKeyFilePath = sshPrivateKeyFilePath
    this.privateKeyPassphrase = privateKeyPassphrase
    this.tenancyId = tenancyId
    this.region = region

    Region desiredRegion = Region.fromRegionId(this.region)

    Supplier<InputStream> privateKeySupplier = new SimplePrivateKeySupplier(this.sshPrivateKeyFilePath)
    AuthenticationDetailsProvider provider = SimpleAuthenticationDetailsProvider.builder()
      .userId(this.userId)
      .fingerprint(this.fingerprint)
      .privateKeySupplier(privateKeySupplier)
      .passPhrase(this.privateKeyPassphrase)
      .tenantId(this.tenancyId)
      .build()

    this.computeClient = new ComputeClient(provider)
    this.computeClient.setRegion(desiredRegion)
    this.networkClient = new VirtualNetworkClient(provider)
    this.networkClient.setRegion(desiredRegion)
    this.objectStorageClient = new ObjectStorageClient(provider)
    this.objectStorageClient.setRegion(desiredRegion)
    this.identityClient = new IdentityClient(provider)
    this.identityClient.setRegion(desiredRegion)
    this.loadBalancerClient = new LoadBalancerClient(provider)
    this.loadBalancerClient.setRegion(desiredRegion)
    this.regions = [new OracleRegion(name: desiredRegion.regionId,
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
    String userId
    String fingerprint
    String sshPrivateKeyFilePath
    String privateKeyPassphrase
    String tenancyId
    String region

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

    Builder compartmentId(String compartmentId) {
      this.compartmentId = compartmentId
      return this
    }

    Builder userId(String userId) {
      this.userId = userId
      return this
    }

    Builder fingerprint(String fingerprint) {
      this.fingerprint = fingerprint
      return this
    }

    Builder sshPrivateKeyFilePath(String sshPrivateKeyFilePath) {
      this.sshPrivateKeyFilePath = sshPrivateKeyFilePath
      return this
    }

    Builder privateKeyPassphrase(String privateKeyPassphrase) {
      this.privateKeyPassphrase = privateKeyPassphrase
      return this
    }

    Builder tenancyId(String tenancyId) {
      this.tenancyId = tenancyId
      return this
    }

    Builder region(String region) {
      this.region = region
      return this
    }

    OracleNamedAccountCredentials build() {
      return new OracleNamedAccountCredentials(
        this.name,
        this.environment,
        this.accountType,
        this.requiredGroupMembership,
        this.compartmentId,
        this.userId,
        this.fingerprint,
        this.sshPrivateKeyFilePath,
        this.privateKeyPassphrase,
        this.tenancyId,
        this.region)
    }
  }

  class OracleRegion {

    String name
    List<String> availabilityZones
  }
}
