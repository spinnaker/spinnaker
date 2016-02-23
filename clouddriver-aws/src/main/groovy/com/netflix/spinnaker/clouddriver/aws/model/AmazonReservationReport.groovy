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
import com.netflix.spinnaker.clouddriver.model.ReservationReport

import java.util.concurrent.atomic.AtomicInteger

class AmazonReservationReport implements ReservationReport {
  Date start
  Date end
  String type = "aws"

  Collection<Map> accounts = []
  Collection<OverallReservationDetail> reservations = []

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

  @JsonPropertyOrder(["availabilityZone", "region", "availabilityZoneId", "instanceType", "os", "totalReserved", "totalUsed", "totalSurplus", "details"])
  static class OverallReservationDetail {
    String availabilityZone
    String instanceType
    OperatingSystemType os
    AtomicInteger totalReserved = new AtomicInteger(0)
    AtomicInteger totalUsed = new AtomicInteger(0)

    Map<String, AccountReservationDetail> accounts = [:].withDefault { String accountName ->
      new AccountReservationDetail()
    }

    @JsonProperty
    int totalSurplus() {
      return (totalReserved.intValue() - totalUsed.intValue())
    }

    @JsonProperty
    String region() {
      return availabilityZone[0..-2]
    }

    @JsonProperty
    String availabilityZoneId() {
      return availabilityZone[-1..-1]
    }
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  static class AccountReservationDetail {
    AtomicInteger reserved = new AtomicInteger(0)
    AtomicInteger used = new AtomicInteger(0)
    AtomicInteger reservedVpc = new AtomicInteger(0)
    AtomicInteger usedVpc = new AtomicInteger(0)

    @JsonProperty
    int surplus() {
      return (reserved.intValue() - used.intValue())
    }

    @JsonProperty
    Integer surplusVpc() {
      if (reservedVpc == null || usedVpc == null) {
        return null
      }

      return (reservedVpc.intValue() - usedVpc.intValue())
    }
  }
}
