/*
 * Copyright (c) 2017 Oracle America, Inc.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */
package com.netflix.spinnaker.clouddriver.oraclebmcs.model

import com.fasterxml.jackson.annotation.JsonInclude
import com.netflix.spinnaker.clouddriver.model.SecurityGroup
import com.netflix.spinnaker.clouddriver.model.SecurityGroupSummary
import com.netflix.spinnaker.clouddriver.model.securitygroups.Rule
import com.netflix.spinnaker.clouddriver.oraclebmcs.OracleBMCSCloudProvider
import groovy.transform.Immutable

@Immutable
@JsonInclude(JsonInclude.Include.NON_EMPTY)
class OracleBMCSSecurityGroup implements SecurityGroup {
  final String type = OracleBMCSCloudProvider.ID
  final String cloudProvider = OracleBMCSCloudProvider.ID
  final String id
  final String name
  final String description
  final String application
  final String accountName
  final String region
  final String network
  final Set<Rule> inboundRules
  final Set<Rule> outboundRules

  @Override
  SecurityGroupSummary getSummary() {
    new OracleBMCSSecurityGroupSummary(name: name, id: id, network: network)
  }
}

