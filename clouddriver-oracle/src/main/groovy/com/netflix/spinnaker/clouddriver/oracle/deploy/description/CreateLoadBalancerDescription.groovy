/*
 * Copyright (c) 2017 Oracle America, Inc.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */
package com.netflix.spinnaker.clouddriver.oracle.deploy.description

import com.netflix.spinnaker.clouddriver.security.resources.ApplicationNameable
import com.oracle.bmc.loadbalancer.model.CertificateDetails
import com.oracle.bmc.loadbalancer.model.BackendSetDetails
import com.oracle.bmc.loadbalancer.model.ListenerDetails
import groovy.transform.ToString

@ToString
class CreateLoadBalancerDescription extends AbstractOracleCredentialsDescription implements ApplicationNameable {

  String application
  String stack
  String detail
  String shape
  String policy
  Boolean isPrivate
  List<String> subnetIds
  Map<String, ListenerDetails> listeners
  Map<String, CertificateDetails> certificates
  Map<String, BackendSetDetails> backendSets
  String loadBalancerId //TODO UpdateRequest comes with id
  
  String clusterName() {
    application + (stack? '-' + stack : '')
  }

  //see NameBuilder.combineAppStackDetail
  String qualifiedName() {
    def stack = this.stack?: ""
    def detail = this.detail
    if (detail) {
      return this.application + "-" + stack + "-" + detail
    }
    if (!stack.isEmpty()) {
      return this.application + "-" + stack
    }
  }
}
