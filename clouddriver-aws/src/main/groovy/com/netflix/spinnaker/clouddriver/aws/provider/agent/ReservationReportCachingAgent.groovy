/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.aws.provider.agent

import com.amazonaws.services.ec2.model.DescribeInstancesRequest
import com.amazonaws.services.ec2.model.DescribeReservedInstancesRequest
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.netflix.spinnaker.cats.agent.AgentDataType
import com.netflix.spinnaker.cats.agent.CacheResult
import com.netflix.spinnaker.cats.agent.CachingAgent
import com.netflix.spinnaker.cats.agent.DefaultCacheResult
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.aws.security.AmazonCredentials
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import com.netflix.spinnaker.clouddriver.aws.model.AmazonReservationReport
import com.netflix.spinnaker.clouddriver.aws.provider.AwsProvider
import groovy.util.logging.Slf4j

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE
import static com.netflix.spinnaker.clouddriver.aws.data.Keys.Namespace.RESERVATION_REPORTS

@Slf4j
class ReservationReportCachingAgent implements CachingAgent {
  private static final Collection<AgentDataType> types = Collections.unmodifiableCollection([
    AUTHORITATIVE.forType(RESERVATION_REPORTS.ns)
  ])

  @Override
  String getProviderName() {
    AwsProvider.PROVIDER_NAME
  }

  @Override
  String getAgentType() {
    "${ReservationReportCachingAgent.simpleName}"
  }

  @Override
  Collection<AgentDataType> getProvidedDataTypes() {
    types
  }

  final AmazonClientProvider amazonClientProvider
  final Collection<NetflixAmazonCredentials> accounts
  final ObjectMapper objectMapper

  ReservationReportCachingAgent(AmazonClientProvider amazonClientProvider,
                                Collection<NetflixAmazonCredentials> accounts,
                                ObjectMapper objectMapper) {
    this.amazonClientProvider = amazonClientProvider
    this.accounts = accounts
    this.objectMapper = objectMapper.enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
  }

  static class MutableCacheData implements CacheData {
    final String id
    int ttlSeconds = -1
    final Map<String, Object> attributes = [:]
    final Map<String, Collection<String>> relationships = [:].withDefault { [] as Set }

    public MutableCacheData(String id) {
      this.id = id
    }

    @JsonCreator
    public MutableCacheData(@JsonProperty("id") String id,
                            @JsonProperty("attributes") Map<String, Object> attributes,
                            @JsonProperty("relationships") Map<String, Collection<String>> relationships) {
      this(id);
      this.attributes.putAll(attributes);
      this.relationships.putAll(relationships);
    }
  }

  public Collection<NetflixAmazonCredentials> getAccounts() {
    return accounts;
  }

  @Override
  CacheResult loadData(ProviderCache providerCache) {
    log.info("Describing items in ${agentType}")
    Map<String, AmazonReservationReport.OverallReservationDetail> reservations = [:].withDefault { String key ->
      def (availabilityZone, operatingSystemType, instanceType) = key.split(":")
      new AmazonReservationReport.OverallReservationDetail(
        availabilityZone: availabilityZone,
        os: AmazonReservationReport.OperatingSystemType.valueOf(operatingSystemType as String),
        instanceType: instanceType,
      )
    }

    def amazonReservationReport = new AmazonReservationReport(start: new Date())
    accounts.each { NetflixAmazonCredentials credentials ->
      amazonReservationReport.accounts << [
        accountId: credentials.accountId,
        name: credentials.name,
        regions: credentials.regions*.name
      ]
    }

    Set<String> processedAccountIds = []
    accounts.sort { it.name }.each { NetflixAmazonCredentials credentials ->
      if (processedAccountIds.contains(credentials.accountId)) {
        log.info("Already processed account (accountId: ${credentials.accountId} / ${credentials.name})")
        return
      }

      credentials.regions.each { AmazonCredentials.AWSRegion region ->
        log.info("Fetching reservation report for ${credentials.name}:${region.name}")

        def amazonEC2 = amazonClientProvider.getAmazonEC2(credentials, region.name)
        def reservedInstancesResult = amazonEC2.describeReservedInstances(new DescribeReservedInstancesRequest())
        reservedInstancesResult.reservedInstances.findAll {
          it.state.equalsIgnoreCase("active") &&
          ["Heavy Utilization", "Partial Upfront", "All Upfront", "No Upfront"].contains(it.offeringType)
        }.each {
          def productDescription = operatingSystemType(it.productDescription)
          def reservation = reservations["${it.availabilityZone}:${productDescription}:${it.instanceType}"]
          reservation.totalReserved.addAndGet(it.instanceCount)
          reservation.accounts[credentials.name].reserved.addAndGet(it.instanceCount)
        }

        def describeInstancesRequest = new DescribeInstancesRequest()
        def allowedStates = ["pending", "running", "shutting-down", "stopping", "stopped"] as Set<String>
        while (true) {
          def result = amazonEC2.describeInstances(describeInstancesRequest)
          result.reservations.each {
            it.getInstances().each {
              if (!allowedStates.contains(it.state.name.toLowerCase())) {
                return
              }

              def productDescription = operatingSystemType(it.platform ? "Windows" : "Linux/UNIX")
              def reservation = reservations["${it.placement.availabilityZone}:${productDescription}:${it.instanceType}"]
              reservation.totalUsed.incrementAndGet()
              reservation.accounts[credentials.name].used.incrementAndGet()
            }
          }

          if (result.nextToken) {
            describeInstancesRequest.withNextToken(result.nextToken)
          } else {
            break
          }
        }
      }

      processedAccountIds << credentials.accountId
    }

    amazonReservationReport.end = new Date()
    amazonReservationReport.reservations = reservations.values().sort {
      a,b -> a.availabilityZone <=> b.availabilityZone ?: a.instanceType <=> b.instanceType ?: a.os <=> b.os
    }
    log.info("Caching ${reservations.size()} items in ${agentType}")
    return new DefaultCacheResult(
      (RESERVATION_REPORTS.ns): [new MutableCacheData("latest", ["report": amazonReservationReport], [:])]
    )
  }

  static AmazonReservationReport.OperatingSystemType operatingSystemType(String productDescription) {
    switch (productDescription.toUpperCase()) {
      case "Linux/UNIX".toUpperCase():
        return AmazonReservationReport.OperatingSystemType.LINUX
      case "Linux/UNIX (Amazon VPC)".toUpperCase():
        return AmazonReservationReport.OperatingSystemType.LINUX
      case "Windows".toUpperCase():
        return AmazonReservationReport.OperatingSystemType.WINDOWS
      case "Windows (Amazon VPC)".toUpperCase():
        return AmazonReservationReport.OperatingSystemType.WINDOWS
      case "Red Hat Enterprise Linux".toUpperCase():
        return AmazonReservationReport.OperatingSystemType.RHEL
      case "Windows with SQL Server Standard".toUpperCase():
        return AmazonReservationReport.OperatingSystemType.WINDOWS_SQL_SERVER
      default:
        log.error("Unknown product description (${productDescription})")
        return AmazonReservationReport.OperatingSystemType.UNKNOWN
    }
  }
}
