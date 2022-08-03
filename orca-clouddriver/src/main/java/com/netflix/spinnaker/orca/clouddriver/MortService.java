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

package com.netflix.spinnaker.orca.clouddriver;

import com.fasterxml.jackson.annotation.JsonRawValue;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.JsonNode;
import groovy.transform.EqualsAndHashCode;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import retrofit.http.GET;
import retrofit.http.Path;
import retrofit.http.Query;

public interface MortService {
  @GET("/securityGroups/{account}/{type}/{region}/{securityGroupName}")
  SecurityGroup getSecurityGroup(
      @Path("account") String account,
      @Path("type") String type,
      @Path("securityGroupName") String securityGroupName,
      @Path("region") String region);

  @GET("/securityGroups/{account}/{type}/{region}/{securityGroupName}")
  SecurityGroup getSecurityGroup(
      @Path("account") String account,
      @Path("type") String type,
      @Path("securityGroupName") String securityGroupName,
      @Path("region") String region,
      @Query("vpcId") String vpcId);

  @GET("/vpcs")
  Collection<VPC> getVPCs();

  @GET("/search")
  List<SearchResult> getSearchResults(@Query("q") String searchTerm, @Query("type") String type);

  @GET("/credentials/{account}")
  Map getAccountDetails(@Path("account") String account);

  class SearchResult {
    int totalMatches;
    int pageNumber;
    int pageSize;
    String platform;
    String query;
    List<Map> results;
  }

  @EqualsAndHashCode
  @Data
  class SecurityGroup {
    String type;
    String id;
    String name;
    Object description;
    String accountName;
    String region;
    String vpcId;
    List<Map> inboundRules;

    // Custom Jackson settings to handle either String or JSON Object for description
    @JsonRawValue
    String getDescription() {
      return description != null ? description.toString() : null;
    }

    void setDescription(String description) {
      this.description = description;
    }

    @JsonSetter("description")
    void setDescription(JsonNode node) {
      // If it's a simple text node, unwrap it to its value
      if (node.isTextual()) {
        this.description = node.textValue();
      } else {
        this.description = node;
      }
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class SecurityGroupIngress {
      String name;
      int startPort;
      int endPort;
      String type;

      public SecurityGroupIngress(String name) {
        this.name = name;
      }
    }

    static List<SecurityGroupIngress> applyMappings(
        Map<String, String> securityGroupMappings,
        List<SecurityGroupIngress> securityGroupIngress) {
      securityGroupMappings =
          securityGroupMappings != null ? securityGroupMappings : new HashMap<>();
      for (SecurityGroupIngress ingress : securityGroupIngress) {
        ingress.setName(securityGroupMappings.getOrDefault(ingress.getName(), ingress.getName()));
      }
      return securityGroupIngress;
    }

    static List<SecurityGroupIngress> filterForSecurityGroupIngress(
        MortService mortService, SecurityGroup currentSecurityGroup) {
      if (currentSecurityGroup == null) {
        return List.of();
      }

      return currentSecurityGroup.inboundRules.stream()
          .filter(inboundRule -> inboundRule.get("securityGroup") != null)
          .map(
              inboundRule -> {
                Map<String, Object> securityGroup =
                    (Map<String, Object>) inboundRule.get("securityGroup");
                String securityGroupName = (String) securityGroup.get("name");

                if (securityGroupName == null) {
                  List<SearchResult> searchResults =
                      mortService.getSearchResults(
                          (String) securityGroup.get("id"), "securityGroups");
                  securityGroupName =
                      searchResults != null
                          ? (String) searchResults.get(0).results.get(0).get("name")
                          : (String) securityGroup.get("id");
                }

                List<Map<String, Object>> portRanges =
                    (List<Map<String, Object>>) inboundRule.get("portRanges");
                final String finalSecurityGroupName = securityGroupName;
                return portRanges.stream()
                    .map(
                        pr ->
                            new SecurityGroupIngress(
                                finalSecurityGroupName,
                                (int) pr.get("startPort"),
                                (int) pr.get("endPort"),
                                (String) inboundRule.get("protocol")))
                    .collect(Collectors.toList());
              })
          .flatMap(Collection::stream)
          .collect(Collectors.toList());
    }

    static SecurityGroup findById(MortService mortService, String securityGroupId) {
      List<SearchResult> searchResults =
          mortService.getSearchResults(securityGroupId, "securityGroups");

      Map securityGroup = null;
      if (!searchResults.isEmpty()
          && searchResults.get(0) != null
          && searchResults.get(0).results != null
          && searchResults.get(0).results.get(0) != null) {
        securityGroup = searchResults.get(0).results.get(0);
      }

      if (securityGroup == null || securityGroup.get("name") == null) {
        throw new IllegalArgumentException(
            String.format("Security group (%s) does not exist", securityGroupId));
      }

      return mortService.getSecurityGroup(
          (String) securityGroup.getOrDefault("account", null),
          searchResults.get(0).platform,
          (String) securityGroup.get("name"),
          (String) securityGroup.getOrDefault("region", null),
          (String) securityGroup.getOrDefault("vpcId", null));
    }
  }

  class VPC {
    String id;
    String name;
    String region;
    String account;

    static VPC findForRegionAndAccount(
        Collection<VPC> allVPCs, String sourceVpcIdOrName, String region, String account) {
      VPC sourceVpc =
          allVPCs.stream()
              .filter(
                  vpc ->
                      vpc.id.equalsIgnoreCase(sourceVpcIdOrName)
                          || (sourceVpcIdOrName.equalsIgnoreCase(vpc.name)
                              && vpc.region.equals(region)
                              && vpc.account.equals(account)))
              .findFirst()
              .orElse(null);

      final String sourceVpcName = sourceVpc != null ? sourceVpc.name : null;

      VPC targetVpc =
          allVPCs.stream()
              .filter(
                  vpc ->
                      (vpc.name != null && vpc.name.equalsIgnoreCase(sourceVpcName))
                          && (vpc.region != null && vpc.region.equalsIgnoreCase(region))
                          && (vpc.account != null && vpc.account.equalsIgnoreCase(account)))
              .findFirst()
              .orElse(null);

      if (targetVpc == null) {
        throw new IllegalStateException(
            String.format(
                "No matching VPC found (vpcId: %s, region: %s, account: %s",
                sourceVpcName, region, account));
      }

      return targetVpc;
    }
  }
}
