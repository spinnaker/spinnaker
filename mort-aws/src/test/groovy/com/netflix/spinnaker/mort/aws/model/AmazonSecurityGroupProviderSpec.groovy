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

package com.netflix.spinnaker.mort.aws.model

import com.netflix.spinnaker.mort.aws.cache.Keys
import com.netflix.spinnaker.mort.model.CacheService
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject

class AmazonSecurityGroupProviderSpec extends Specification {

  @Subject
  AmazonSecurityGroupProvider provider

  @Shared
  CacheService cacheService

  def setup() {
    cacheService = Mock(CacheService)
    provider = new AmazonSecurityGroupProvider(cacheService: cacheService)
  }

  void "getAll lists all"() {
    when:
    List<AmazonSecurityGroup> allGroups = getAllGroups()
    def result = provider.getAll()

    then:
    result.size() == 8
    1 * cacheService.keysByType(Keys.Namespace.SECURITY_GROUPS) >> allGroups.collect { makeKey(it) }
    allGroups.each { AmazonSecurityGroup group ->
      1 * cacheService.retrieve(makeKey(group), AmazonSecurityGroup) >> group
    }
    0 * _
  }

  void "getAllByRegion lists only those in supplied region"() {
    given:
    String region = 'us-east-1'
    when:
    List<AmazonSecurityGroup> allGroups = getAllGroups()
    def result = provider.getAllByRegion(region)

    then:
    result.size() == 4
    result.each {
      it.region == region
    }
    1 * cacheService.keysByType(Keys.Namespace.SECURITY_GROUPS) >> allGroups.collect { makeKey(it) }
    allGroups
        .findAll { it.region == region }
        .each { AmazonSecurityGroup group ->
      println group.toString()
          1 * cacheService.retrieve(makeKey(group), AmazonSecurityGroup) >> group
    }
    0 * _
  }

  void "getAllByAccount lists only those in supplied account"() {
    given:
    String account = 'prod'

    when:
    List<AmazonSecurityGroup> allGroups = getAllGroups()
    def result = provider.getAllByAccount(account)

    then:
    result.size() == 4
    result.each {
      it.accountName == account
    }
    1 * cacheService.keysByType(Keys.Namespace.SECURITY_GROUPS) >> allGroups.collect { makeKey(it) }
    allGroups
        .findAll { it.accountName == account }
        .each { AmazonSecurityGroup group ->
      println group.toString()
      1 * cacheService.retrieve(makeKey(group), AmazonSecurityGroup) >> group
    }
    0 * _
  }

  void "getAllByAccountAndRegion lists only those in supplied account and region"() {
    given:
    String account = 'prod'
    String region = 'us-west-1'

    when:
    List<AmazonSecurityGroup> allGroups = getAllGroups()
    def result = provider.getAllByAccountAndRegion(account, region)

    then:
    result.size() == 2
    result.each {
      it.accountName == account
      it.region == region
    }
    1 * cacheService.keysByType(Keys.Namespace.SECURITY_GROUPS) >> allGroups.collect { makeKey(it) }
    allGroups
        .findAll { it.accountName == account && it.region == region }
        .each { AmazonSecurityGroup group ->
      println group.toString()
      1 * cacheService.retrieve(makeKey(group), AmazonSecurityGroup) >> group
    }
    0 * _
  }

  void "getAllByAccountAndName lists only those in supplied account with supplied name"() {
    given:
    String account = 'prod'
    String name = 'a'

    when:
    List<AmazonSecurityGroup> allGroups = getAllGroups()
    def result = provider.getAllByAccountAndName(account, name)

    then:
    result.size() == 2
    result.each {
      it.accountName == account
      it.name == name
    }
    1 * cacheService.keysByType(Keys.Namespace.SECURITY_GROUPS) >> allGroups.collect { makeKey(it) }
    allGroups
        .findAll { it.accountName == account && it.name == name }
        .each { AmazonSecurityGroup group ->
      println group.toString()
      1 * cacheService.retrieve(makeKey(group), AmazonSecurityGroup) >> group
    }
    0 * _
  }

  void "get returns match based on account, region, and name"() {
    given:
    AmazonSecurityGroup expected = getAllGroups()[0]
    List<AmazonSecurityGroup> allGroups = getAllGroups()

    when:
    def result = provider.get(expected.accountName, expected.region, expected.name, null)

    then:
    result == expected
    1 * cacheService.retrieve(makeKey(expected), AmazonSecurityGroup) >> expected
    1 * cacheService.keysByType(Keys.Namespace.SECURITY_GROUPS) >> allGroups.collect { makeKey(it) }
    0 * _
  }

  @Shared Map securityGroupMap = [
      prod: [
          'us-east-1': [
              new AmazonSecurityGroup(id: 'a', name: 'a', region: 'us-east-1', accountName: 'prod'),
              new AmazonSecurityGroup(id: 'b', name: 'b', region: 'us-east-1', accountName: 'prod')
          ],
          'us-west-1': [
              new AmazonSecurityGroup(id: 'a', name: 'a', region: 'us-west-1', accountName: 'prod'),
              new AmazonSecurityGroup(id: 'b', name: 'b', region: 'us-west-1', accountName: 'prod')
          ]
      ],
      test: [
          'us-east-1': [
              new AmazonSecurityGroup(id: 'a', name: 'a', region: 'us-east-1', accountName: 'test'),
              new AmazonSecurityGroup(id: 'b', name: 'b', region: 'us-east-1', accountName: 'test')
          ],
          'us-west-1': [
              new AmazonSecurityGroup(id: 'a', name: 'a', region: 'us-west-1', accountName: 'test'),
              new AmazonSecurityGroup(id: 'b', name: 'b', region: 'us-west-1', accountName: 'test')
          ]
      ]
  ]

  private List<AmazonSecurityGroup> getAllGroups() {
    securityGroupMap.collect {
      it.value.collect {
        it.value
      }.flatten()
    }.flatten()
  }

  private String makeKey(AmazonSecurityGroup securityGroup) {
    Keys.getSecurityGroupKey(securityGroup.name, securityGroup.id, securityGroup.region, securityGroup.accountName, securityGroup.vpcId)
  }
}
