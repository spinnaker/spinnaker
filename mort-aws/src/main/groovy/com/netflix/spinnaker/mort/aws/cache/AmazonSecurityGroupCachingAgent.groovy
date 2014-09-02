/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.mort.aws.cache

import com.amazonaws.services.ec2.AmazonEC2
import com.netflix.frigga.Names
import com.netflix.spinnaker.mort.aws.model.AmazonSecurityGroup
import com.netflix.spinnaker.mort.model.CacheService
import com.netflix.spinnaker.mort.model.CachingAgent
import com.netflix.spinnaker.mort.model.securitygroups.Rule
import com.netflix.spinnaker.mort.model.securitygroups.SecurityGroupRule
import groovy.transform.Immutable
import rx.Observable

@Immutable(knownImmutables = ["ec2", "cacheService"])
class AmazonSecurityGroupCachingAgent implements CachingAgent {
  final String account
  final String region
  final AmazonEC2 ec2
  final CacheService cacheService

  private Set<String> lastRun = []

  @Override
  void call() {
    println "[$account:$region:sgs] - Caching..."
    def result = ec2.describeSecurityGroups()
    def thisRun = []
    Observable.from(result.securityGroups).filter {
      !lastRun.contains(it.groupId)
    }.subscribe {
      thisRun << it.groupId

      Map<String, SecurityGroupRule> rules = [:]

      it.ipPermissions.each { permission ->
        permission.userIdGroupPairs.each { sg ->
          if (!rules.containsKey(sg.groupId)) {
            rules.put(sg.groupId, [
              securityGroup:
                new AmazonSecurityGroup(
                  id: sg.groupId,
                  name: sg.groupName,
                  application: Names.parseName(sg.groupName).app,
                  accountName: account,
                  region: region
                ),
              portRanges   : []])
          }
          rules.get(sg.groupId).portRanges += [ new Rule.PortRange(startPort: permission.fromPort, endPort: permission.toPort)]
        }
      }

      cacheService.put(Keys.getSecurityGroupKey(it.groupName, it.groupId, region, account),
        new AmazonSecurityGroup(
          id: it.groupId,
          name: it.groupName,
          application: Names.parseName(it.groupName).app,
          accountName: account,
          region: region,
          inboundRules: rules.values().collect { rule ->
            new SecurityGroupRule(
              securityGroup: rule.securityGroup,
              portRanges: rule.portRanges
            )
          }
        )
      )
    }
    lastRun = thisRun
  }
}
