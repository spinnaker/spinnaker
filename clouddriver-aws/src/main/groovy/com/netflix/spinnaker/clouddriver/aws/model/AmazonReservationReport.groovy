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

package com.netflix.spinnaker.clouddriver.aws.model

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonPropertyOrder
import com.fasterxml.jackson.annotation.JsonView
import com.netflix.spinnaker.clouddriver.model.ReservationReport

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

@JsonPropertyOrder(["reservations", "start", "end", "accounts", "type"])
class AmazonReservationReport implements ReservationReport {
  @JsonView(Views.V1.class)
  Date start

  @JsonView(Views.V1.class)
  Date end

  @JsonView(Views.V1.class)
  String type = "aws"

  @JsonView(Views.V1.class)
  Collection<Map> accounts = []

  @JsonView(Views.V1.class)
  Collection<OverallReservationDetail> reservations = []

  @JsonView(Views.V1.class)
  Map<String, Collection<String>> errorsByRegion = [:]

  static enum OperatingSystemType {
    LINUX("LINUX", false),
    LINUX_VPC("LINUX", true),
    WINDOWS("WINDOWS", false),
    WINDOWS_VPC("WINDOWS", true),
    WINDOWS_SQL_SERVER("WINDOWS_SQL_SERVER", false),
    RHEL("RHEL", false),
    UNKNOWN("UNKNOWN", false)

    String name
    boolean isVpc

    OperatingSystemType(String name, boolean isVpc) {
      this.name = name
      this.isVpc = isVpc
    }
  }

  static class Views {
    static class V1 {

    }

    static class V2 extends V1 {

    }

    static class V3 extends V2 {

    }
  }

  @JsonPropertyOrder(["availabilityZone", "region", "availabilityZoneId", "instanceType", "os", "totalReserved", "totalUsed", "totalSurplus", "details", "accounts"])
  static class OverallReservationDetail {
    String availabilityZone

    @JsonView(Views.V1.class)
    String instanceType

    // Support for region-scoped RIs
    String region

    @JsonView(Views.V1.class)
    OperatingSystemType os

    @JsonView(Views.V1.class)
    AtomicInteger totalReserved = new AtomicInteger(0)

    @JsonView(Views.V1.class)
    AtomicInteger totalUsed = new AtomicInteger(0)

    AtomicInteger totalAllocated = new AtomicInteger(0)

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonView(Views.V1.class)
    Map<String, AccountReservationDetail> accounts = new ConcurrentHashMap<>()

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonView(Views.V3.class)
    List<Allocation> allocations = []

    @JsonProperty
    @JsonView(Views.V3.class)
    String id() {
      return "${region}:${availabilityZoneId()}:${instanceType}:${os.name.toLowerCase()}"
    }

    @JsonProperty
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonView(Views.V3.class)
    Integer totalAllocated() {
      return totalAllocated.intValue()
    }

    @JsonProperty
    @JsonView(Views.V1.class)
    int totalSurplus() {
      return (totalReserved.intValue() + totalAllocated.intValue() - totalUsed.intValue())
    }

    @JsonProperty
    @JsonView(Views.V1.class)
    String region() {
      return (availabilityZone == null || availabilityZone == '*') ? region : availabilityZone[0..-2]
    }

    @JsonProperty
    @JsonView(Views.V1.class)
    String availabilityZoneId() {
      return (availabilityZone == null || availabilityZone == '*') ? '*' : availabilityZone[-1..-1]
    }

    @JsonProperty
    @JsonView(Views.V1.class)
    String availabilityZone() {
      return availabilityZone == null ? '*' : availabilityZone
    }

    String instanceFamily() {
      return instanceType.substring(0, instanceType.indexOf('.'))
    }

    AccountReservationDetail getAccount(String accountName) {
      def newAccountReservationDetail = new AccountReservationDetail()
      def existingAccountReservationDetail = accounts.putIfAbsent(accountName, newAccountReservationDetail)

      if (existingAccountReservationDetail) {
        return existingAccountReservationDetail
      }

      return newAccountReservationDetail
    }
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  static class AccountReservationDetail {
    @JsonView(Views.V1.class)
    AtomicInteger reserved = new AtomicInteger(0)

    @JsonView(Views.V1.class)
    AtomicInteger used = new AtomicInteger(0)

    @JsonView(Views.V1.class)
    AtomicInteger reservedVpc = new AtomicInteger(0)

    @JsonView(Views.V1.class)
    AtomicInteger usedVpc = new AtomicInteger(0)

    @JsonProperty
    @JsonView(Views.V1.class)
    int surplus() {
      return (reserved.intValue() - used.intValue())
    }

    @JsonProperty
    @JsonView(Views.V1.class)
    Integer surplusVpc() {
      if (reservedVpc == null || usedVpc == null) {
        return null
      }

      return (reservedVpc.intValue() - usedVpc.intValue())
    }
  }

  static class Allocation {
    @JsonView(Views.V3.class)
    String source

    @JsonView(Views.V3.class)
    String sourceInstanceType

    @JsonView(Views.V3.class)
    int sourceInstanceQuantity

    @JsonView(Views.V3.class)
    String target

    @JsonView(Views.V3.class)
    String targetInstanceType

    @JsonView(Views.V3.class)
    int targetInstanceQuantity

    @JsonView(Views.V3.class)
    int index
  }

  static class DescendingOverallReservationDetailComparator implements Comparator<OverallReservationDetail> {
    @Override
    int compare(OverallReservationDetail a, OverallReservationDetail b) {
      def r = a.region <=> b.region

      if (!r) {
        r = a.availabilityZone <=> b.availabilityZone
      }

      if (!r) {
        r = a.instanceFamily() <=> b.instanceFamily()
      }

      if (!r) {
        r = normalizeInstanceType(a.instanceType) <=> normalizeInstanceType(b.instanceType)
      }

      if (!r) {
        r = a.os.name <=> b.os.name
      }

      return -r
    }

    /**
     * @return normalized instance type that can be lexicographically sorted
     */
    static String normalizeInstanceType(String instanceType) {
      def instanceClassRankings = [
        'xlarge': 6,
        'large' : 5,
        'medium': 4,
        'small' : 3,
        'micro' : 2,
        'nano'  : 1,
      ];

      def instanceClassRanking = instanceClassRankings.find { instanceType.contains(it.key) }
      if (!instanceClassRanking) {
        return "0000"
      }

      // extract {{multiplier}} from {{family}}.{{multiplier}}{{size}}
      def multiplier = instanceType.substring(instanceType.indexOf('.') + 1).replaceAll(instanceClassRanking.key, "")

      if (multiplier.length() > 3) {
        throw new IllegalArgumentException("Instance type '${instanceType}' has an unsupported multiplier, must be < 999")
      }

      return instanceClassRanking.value + multiplier.padLeft(3, "0")
    }
  }
}
