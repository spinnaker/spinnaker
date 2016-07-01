/*
 * Copyright 2016 Target, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.netflix.spinnaker.clouddriver.openstack.provider.view

import com.netflix.spinnaker.clouddriver.model.AddressableRange
import com.netflix.spinnaker.clouddriver.model.SecurityGroupProvider
import com.netflix.spinnaker.clouddriver.model.securitygroups.IpRangeRule
import com.netflix.spinnaker.clouddriver.model.securitygroups.Rule
import com.netflix.spinnaker.clouddriver.openstack.OpenstackCloudProvider
import com.netflix.spinnaker.clouddriver.openstack.client.OpenstackClientProvider
import com.netflix.spinnaker.clouddriver.openstack.model.OpenstackSecurityGroup
import com.netflix.spinnaker.clouddriver.openstack.security.OpenstackNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import groovy.util.logging.Slf4j
import org.openstack4j.model.compute.SecGroupExtension
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

/**
 * Provides a view of existing Openstack security groups in all configured Openstack accounts.
 *
 * TODO: Remove direct lookup to Openstack and lookup from cache view instead
 */
@Slf4j
@Component
class OpenstackSecurityGroupProvider implements SecurityGroupProvider<OpenstackSecurityGroup> {

  final AccountCredentialsProvider accountCredentialsProvider

  @Autowired
  OpenstackSecurityGroupProvider(AccountCredentialsProvider accountCredentialsProvider) {
    this.accountCredentialsProvider = accountCredentialsProvider
  }

  Set<OpenstackSecurityGroup> getSecurityGroups(boolean includeRules) {
    // TODO This is not thread safe and not cached
    // This just proves the provider is working once it has a data set.
    Set<OpenstackSecurityGroup> securityGroups = []
    accountCredentialsProvider.all.each { account ->
      if (account instanceof OpenstackNamedAccountCredentials) {
        OpenstackClientProvider provider = ((OpenstackNamedAccountCredentials) account).credentials.provider

        provider.allRegions.each { region ->
          securityGroups.addAll(
            provider.getSecurityGroups(region)
              .flatten()
              .collect { convertToOpenstackSecurityGroup(includeRules, it, account.name, region) }
          )
        }
      }
    }

    securityGroups
  }

  @Override
  String getType() {
    OpenstackCloudProvider.ID
  }

  @Override
  Set<OpenstackSecurityGroup> getAll(boolean includeRules) {
    getSecurityGroups(includeRules)
  }

  @Override
  Set<OpenstackSecurityGroup> getAllByRegion(boolean includeRules, String region) {
    getSecurityGroups(includeRules).findAll { it.region == region }
  }

  @Override
  Set<OpenstackSecurityGroup> getAllByAccount(boolean includeRules, String account) {
    getSecurityGroups(includeRules).findAll { it.accountName == account }
  }

  @Override
  Set<OpenstackSecurityGroup> getAllByAccountAndName(boolean includeRules, String account, String name) {
    getAllByAccount(includeRules, account).findAll { it.name == name }
  }

  @Override
  Set<OpenstackSecurityGroup> getAllByAccountAndRegion(boolean includeRule, String account, String region) {
    getAllByAccount(includeRule, account).findAll { it.region == region }
  }

  @Override
  OpenstackSecurityGroup get(String account, String region, String name, String vpcId) {
    getAllByAccountAndRegion(true, account, region).find { it.name == name }
  }

  private OpenstackSecurityGroup convertToOpenstackSecurityGroup(boolean includeRules, SecGroupExtension securityGroup, String account, String region) {
    List<Rule> inboundRules = includeRules ? buildInboundRules(securityGroup) : []

    new OpenstackSecurityGroup(id: securityGroup.id,
      accountName: account,
      region: region,
      name: securityGroup.name,
      description: securityGroup.description,
      inboundRules: inboundRules
    )
  }

  private List<Rule> buildInboundRules(SecGroupExtension securityGroup) {
    securityGroup.rules.collect { sgr ->
      def portRange = new Rule.PortRange(startPort: sgr.fromPort, endPort: sgr.toPort)
      def addressableRange = buildAddressableRangeFromCidr(sgr.range.cidr)
      new IpRangeRule(protocol: sgr.IPProtocol.value(),
                      portRanges: [portRange] as SortedSet,
                      range: addressableRange
      )
    }
  }

  private AddressableRange buildAddressableRangeFromCidr(String cidr) {
    def rangeParts = cidr.split('/') as List

    // If the cidr just a single IP address, use 32 as the mask
    if (rangeParts.size() == 1) {
      rangeParts << "32"
    }

    new AddressableRange(ip: rangeParts[0], cidr: "/${rangeParts[1]}")


  }
}
