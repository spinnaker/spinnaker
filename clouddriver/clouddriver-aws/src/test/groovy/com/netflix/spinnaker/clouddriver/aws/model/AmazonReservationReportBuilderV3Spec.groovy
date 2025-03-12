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

import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

import java.util.concurrent.atomic.AtomicInteger;

class AmazonReservationReportBuilderV3Spec extends Specification {
  @Shared
  def reportBuilder = new AmazonReservationReportBuilder.V3()

  def allocationIndex = new AtomicInteger(0)

  void "should partially cover a shortfall"() {
    given:
    def regional = overallReservationDetail("m4.fxlarge", 16, 0)
    def shortfall = overallReservationDetail("m4.4xlarge", 0, 2)

    when:
    reportBuilder.coverShortfall(allocationIndex, regional, 16, shortfall, 20)

    then:
    allocationIndex.get() == 1

    // shortfall was 8/20 == 40% ... 40% of available surplus == 4 fxlarge == 1 * 4xlarge available to partially cover
    regional.totalSurplus() == 12
    regional.totalRegionalReserved() == -4

    shortfall.totalSurplus() == -1
    shortfall.totalRegionalReserved() == 1
  }

  void "should fully cover a shortfall"() {
    given:
    def regional = overallReservationDetail("m4.fxlarge", 24, 0)
    def shortfall = overallReservationDetail("m4.4xlarge", 0, 2)

    when:
    reportBuilder.coverShortfall(allocationIndex, regional, 24, shortfall, 20)

    then:
    allocationIndex.get() == 1

    // shortfall was 8/20 == 40% ... 40% of available surplus == 9 fxlarge == 2 * 4xlarge available to fully cover
    regional.totalSurplus() == 16
    regional.totalRegionalReserved() == -8

    shortfall.totalSurplus() == 0
    shortfall.totalRegionalReserved() == 2
  }

  void "unable to cover any shortfall"() {
    given:
    def regional = overallReservationDetail("m4.fxlarge", 8, 0)
    def shortfall = overallReservationDetail("m4.4xlarge", 0, 2)

    when:
    reportBuilder.coverShortfall(allocationIndex, regional, 8, shortfall, 20)

    then:
    allocationIndex.get() == 0

    // shortfall was 8/20 == 40% ... 40% of available surplus == 3 fxlarge == 0 * 4xlarge available to cover shortfall
    regional.totalSurplus() == 8
    regional.totalRegionalReserved() == 0

    shortfall.totalSurplus() == -2
    shortfall.totalRegionalReserved() == 0
  }

  @Unroll
  void "should determine multiplier relative to 'xlarge'"() {
    given:
    def support = new AmazonReservationReportBuilder.Support()

    expect:
    support.getMultiplier(instanceType) == multiplier

    where:
    instanceType   || multiplier
    "m4.16xlarge"  || 16
    "m4.xlarge"    || 1
    "m4.xwhoknows" || 0
    "m4.large"     || 0.5
  }

  private AmazonReservationReport.OverallReservationDetail overallReservationDetail(String instanceType,
                                                                                    int totalReserved,
                                                                                    int totalUsed) {
    return new AmazonReservationReport.OverallReservationDetail(
      totalReserved: new AtomicInteger(totalReserved),
      totalUsed: new AtomicInteger(totalUsed),
      instanceType: instanceType,
      os: AmazonReservationReport.OperatingSystemType.LINUX
    )
  }
}
