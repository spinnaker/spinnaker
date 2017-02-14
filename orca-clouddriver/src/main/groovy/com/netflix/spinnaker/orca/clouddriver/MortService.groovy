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


package com.netflix.spinnaker.orca.clouddriver

import groovy.transform.Canonical
import groovy.transform.EqualsAndHashCode
import retrofit.http.GET
import retrofit.http.Path
import retrofit.http.Query

interface MortService {
  @GET("/securityGroups/{account}/{type}/{region}/{securityGroupName}")
  SecurityGroup getSecurityGroup(
    @Path("account") String account,
    @Path("type") String type,
    @Path("securityGroupName") String securityGroupName,
    @Path("region") String region)

  @GET("/securityGroups/{account}/{type}/{region}/{securityGroupName}")
  SecurityGroup getSecurityGroup(
    @Path("account") String account,
    @Path("type") String type,
    @Path("securityGroupName") String securityGroupName,
    @Path("region") String region,
    @Query("vpcId") String vpcId)

  @GET("/vpcs")
  Collection<VPC> getVPCs()

  @GET("/search")
  List<SearchResult> getSearchResults(@Query("q") String searchTerm,
                                      @Query("type") String type)

  @GET("/credentials/{account}")
  Map getAccountDetails(@Path("account") String account)

  static class SearchResult {
    int totalMatches
    int pageNumber
    int pageSize
    String platform
    String query
    List<Map> results
  }

  @EqualsAndHashCode
  static class SecurityGroup {
    String type
    String id
    String name
    String description
    String accountName
    String region
    String vpcId
    List<Map> inboundRules

    @Canonical
    static class SecurityGroupIngress {
      String name
      int startPort
      int endPort
      String type
    }

    static List<SecurityGroupIngress> applyMappings(Map<String, String> securityGroupMappings,
                                                    List<SecurityGroupIngress> securityGroupIngress) {
      securityGroupMappings = securityGroupMappings ?: [:]
      securityGroupIngress.collect {
        it.name = securityGroupMappings[it.name as String] ?: it.name
        it
      }
    }

    static List<SecurityGroupIngress> filterForSecurityGroupIngress(MortService mortService,
                                                                    SecurityGroup currentSecurityGroup) {
      if (currentSecurityGroup == null) {
        return []
      }

      currentSecurityGroup.inboundRules.findAll {
        it.securityGroup
      }.collect { Map inboundRule ->
        def securityGroupName = inboundRule.securityGroup.name
        if (!securityGroupName) {
          def searchResults = mortService.getSearchResults(inboundRule.securityGroup.id as String, "securityGroups")
          securityGroupName = searchResults ? searchResults[0].results.getAt(0)?.name : inboundRule.securityGroup.id
        }

        inboundRule.portRanges.collect {
          new SecurityGroupIngress(
            securityGroupName as String, it.startPort as int, it.endPort as int, inboundRule.protocol as String
          )
        }
      }.flatten()
    }

    static SecurityGroup findById(MortService mortService, String securityGroupId) {
      def searchResults = mortService.getSearchResults(securityGroupId, "securityGroups")
      def securityGroup = searchResults?.getAt(0)?.results?.getAt(0)

      if (!securityGroup?.name) {
        throw new IllegalArgumentException("Security group (${securityGroupId}) does not exist")
      }

      return mortService.getSecurityGroup(
        securityGroup.account as String,
        searchResults[0].platform,
        securityGroup.name as String,
        securityGroup.region as String,
        securityGroup.vpcId as String
      )
    }
  }

  static class VPC {
    String id
    String name
    String region
    String account

    static VPC findForRegionAndAccount(Collection<MortService.VPC> allVPCs,
                                       String sourceVpcId,
                                       String region,
                                       String account) {
      def sourceVpc = allVPCs.find { it.id == sourceVpcId }
      def targetVpc = allVPCs.find { it.name == sourceVpc?.name && it.region == region && it.account == account }

      if (!targetVpc) {
        throw new IllegalStateException("No matching VPC found (vpcId: ${sourceVpcId}, region: ${region}, account: ${account}")
      }

      return targetVpc
    }
  }
}
