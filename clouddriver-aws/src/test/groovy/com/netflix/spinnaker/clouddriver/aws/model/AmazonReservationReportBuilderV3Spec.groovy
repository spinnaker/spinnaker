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

import com.netflix.spinnaker.clouddriver.aws.model.AmazonReservationReport.OverallReservationDetail
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
    def regional = overallReservationDetail("m4.xlarge", 5, 0)
    def shortfall = overallReservationDetail("m4.4xlarge", 0, 2)

    when:
    reportBuilder.coverShortfall(allocationIndex, regional, shortfall)

    then:
    allocationIndex.get() == 1

    // took 4 * xlarge to partially cover shortfall (4 * xlarge == 1 * 4xlarge)
    regional.totalSurplus() == 1
    regional.totalRegionalReserved() == -4

    shortfall.totalSurplus() == -1
    shortfall.totalRegionalReserved() == 1
  }

  void "should fully cover a shortfall"() {
    given:
    def regional = overallReservationDetail("m4.xlarge", 10, 0)
    def shortfall = overallReservationDetail("m4.4xlarge", 0, 2)

    when:
    reportBuilder.coverShortfall(allocationIndex, regional, shortfall)

    then:
    allocationIndex.get() == 1

    // took 8 * 4xlarge to fully cover shortfall (8 * xlarge == 2 * 4xlarge)
    regional.totalSurplus() == 2
    regional.totalRegionalReserved() == -8

    shortfall.totalSurplus() == 0
    shortfall.totalRegionalReserved() == 2
  }

  void "unable to cover any shortfall"() {
    given:
    def regional = overallReservationDetail("m4.xlarge", 1, 0)
    def shortfall = overallReservationDetail("m4.4xlarge", 0, 2)

    when:
    reportBuilder.coverShortfall(allocationIndex, regional, shortfall)

    then:
    allocationIndex.get() == 0

    // took 8 * 4xlarge to fully cover shortfall (8 * xlarge == 2 * 4xlarge)
    regional.totalSurplus() == 1
    regional.totalRegionalReserved() == 0

    shortfall.totalSurplus() == -2
    shortfall.totalRegionalReserved() == 0
  }

  @Unroll
  void "should determine multiplier relative to 'xlarge'"() {
    expect:
    reportBuilder.getMultiplier(instanceType) == multiplier

    where:
    instanceType   || multiplier
    "m4.16xlarge"  || 16
    "m4.xlarge"    || 1
    "m4.xwhoknows" || 0
    "m4.large"     || 0.5
  }

  private OverallReservationDetail overallReservationDetail(String instanceType, int totalReserved, int totalUsed) {
    return new OverallReservationDetail(
      totalReserved: new AtomicInteger(totalReserved),
      totalUsed: new AtomicInteger(totalUsed),
      instanceType: instanceType,
      os: AmazonReservationReport.OperatingSystemType.LINUX
    )
  }

}
