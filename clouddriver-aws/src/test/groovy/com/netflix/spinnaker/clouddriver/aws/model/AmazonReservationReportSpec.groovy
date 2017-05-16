/*
 * Copyright 2017 Netflix, Inc.
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

import spock.lang.Specification
import spock.lang.Unroll;

import static com.netflix.spinnaker.clouddriver.aws.model.AmazonReservationReport.OperatingSystemType.*
import static com.netflix.spinnaker.clouddriver.aws.model.AmazonReservationReport.*

class AmazonReservationReportSpec extends Specification {
  @Unroll
  def "should normalize instance types by size and multiplier"() {
    given:
    def comparator = new DescendingOverallReservationDetailComparator()

    expect:
    comparator.normalizeInstanceType(left) > comparator.normalizeInstanceType(right)

    and:
    comparator.normalizeInstanceType("m4.16xlarge") == "6016"
    comparator.normalizeInstanceType("m4.16large") == "5016"
    comparator.normalizeInstanceType("m4.10medium") == "4010"
    comparator.normalizeInstanceType("m4.8small") == "3008"
    comparator.normalizeInstanceType("m4.3micro") == "2003"
    comparator.normalizeInstanceType("m4.999nano") == "1999"

    // unknown size should always normalize to '0000'
    comparator.normalizeInstanceType("m4.4unknown") == "0000"

    try {
      comparator.normalizeInstanceType("m4.1000nano")
      assert false
    } catch (IllegalArgumentException e) {
      assert e.message.contains("must be < 999")
    }

    where:
    left          | right         || _
    "c4.16xlarge" | "c4.10xlarge" || _
    "c4.10xlarge" | "c4.8xlarge"  || _
    "c4.4xlarge"  | "c4.2xlarge"  || _
    "c4.2xlarge"  | "c4.xlarge"   || _
    "c4.xlarge"   | "c4.large"    || _
    "c4.large"    | "c4.small"    || _
    "c4.small"    | "c4.16nano"   || _
    "c4.nano"     | "c4.unknown"  || _
  }

  def "should support regional reservations w/o availabilityZone"() {
    given:
    def report = new AmazonReservationReport(
      reservations: [
        new OverallReservationDetail(
          instanceType: "m4.xlarge",
          availabilityZone: "*",
          region: "us-west-2",
          os: LINUX
        )
      ]
    )

    expect:
    report.reservations[0].availabilityZoneId() == "*"
    report.reservations[0].availabilityZone() == "*"
    report.reservations[0].region() == "us-west-2"
  }

  def "should sort reservations by normalized instance type ranking"() {
    given:
    def reservations = ["c4.small", "c4.4xlarge", "c4.large", "c4.16xlarge", "c4.8xlarge"].collect {
      new OverallReservationDetail(
        instanceType: it
      )
    }

    when:
    def sortedReservations = reservations.sort(false, new DescendingOverallReservationDetailComparator())

    then:
    sortedReservations*.instanceType == [
      "c4.16xlarge", "c4.8xlarge", "c4.4xlarge", "c4.large", "c4.small"
    ]
  }

  @Unroll
  def "should compare by region, az, family, normalized instance type, os"() {
    given:
    def comparator = new DescendingOverallReservationDetailComparator()

    def left = new AmazonReservationReport.OverallReservationDetail(
      region: lRegion,
      availabilityZone: lAvailabilityZone,
      instanceType: lInstanceType,
      os: lOs
    )

    def right = new AmazonReservationReport.OverallReservationDetail(
      region: rRegion,
      availabilityZone: rAvailabilityZone,
      instanceType: rInstanceType,
      os: rOs
    )

    expect:
    comparator.compare(left, right) == expected

    where:
    lRegion     | lAvailabilityZone | lInstanceType | lOs   | rRegion     | rAvailabilityZone | rInstanceType | rOs     || expected
    "us-west-1" | "us-west-1c"      | "c4.4xlarge"  | LINUX | "us-west-1" | "us-west-1c"      | "c4.4xlarge"  | LINUX   || 0
    "us-west-1" | "us-west-1c"      | "c4.4xlarge"  | LINUX | "us-west-2" | "us-west-2a"      | "c4.4xlarge"  | LINUX   || 1    // us-west-2 > us-west-1
    "us-west-1" | "us-west-1c"      | "c4.4xlarge"  | LINUX | "us-east-1" | "us-east-1c"      | "c4.4xlarge"  | LINUX   || -1   // us-west-1 > us-east-1
    "us-west-1" | "us-west-1c"      | "c4.4xlarge"  | LINUX | "us-west-1" | "us-west-1d"      | "c4.4xlarge"  | LINUX   || 1    // us-west-1d > us-west-1c
    "us-west-1" | "us-west-1c"      | "c4.16xlarge" | LINUX | "us-west-1" | "us-west-1c"      | "c4.4xlarge"  | LINUX   || -1   // c4.16xlarge > c4.4xlarge
    "us-west-1" | "us-west-1c"      | "c4.4xlarge"  | LINUX | "us-west-1" | "us-west-1c"      | "c4.4xlarge"  | WINDOWS || 1    // windows > linux
  }
}
