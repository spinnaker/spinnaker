/*
 * Copyright 2018 Netflix, Inc.
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

import com.netflix.spinnaker.clouddriver.aws.provider.view.AmazonS3DataProvider
import com.netflix.spinnaker.clouddriver.model.DataProvider
import spock.lang.Shared
import spock.lang.Specification

import java.util.concurrent.atomic.AtomicInteger;

class AmazonReservationReportBuilderV4Spec extends Specification {
  def dataProvider = Mock(AmazonS3DataProvider)

  @Shared
  def reportBuilder = new AmazonReservationReportBuilder.V4()

  void "should noop if 'rri_weights' does not exist"() {
    given:
    def source = new AmazonReservationReport(
      reservations: [
        overallReservationDetail("r4.4xlarge", 100, 100, "us-west-2", "us-west-2a")
      ]
    )

    when:
    def v4Report = reportBuilder.build(dataProvider, source)

    then:
    1 * dataProvider.supportsIdentifier(DataProvider.IdentifierType.Static, "rri_weights") >> { return false }

    v4Report == source
  }

  void "should partition surplus by 'rri_weights'"() {
    given:
    def source = new AmazonReservationReport(
      reservations: [
        overallReservationDetail("r4.2xlarge", 0, 0, "us-west-2", "us-west-2a"),
        overallReservationDetail("r4.2xlarge", 0, 0, "us-west-2", "us-west-2b"),
        overallReservationDetail("r4.2xlarge", 0, 0, "us-west-2", "us-west-2c"),
        overallReservationDetail("r4.8xlarge", 0, 0, "us-west-2", "us-west-2a"),
        overallReservationDetail("r4.fxlarge", 1000, 0, "us-west-2", "*"),

        overallReservationDetail("m4.2xlarge", 100, 0, "us-west-2", "us-west-2a"),

        overallReservationDetail("m5.2xlarge", 0, 0, "us-west-2", "us-west-2a"),
        overallReservationDetail("m5.fxlarge", 1, 0, "us-west-2", "*"),
      ]
    )

    when:
    def v4Report = reportBuilder.build(dataProvider, source)
    def reservations = v4Report.reservations.groupBy { it.id() }

    then:
    1 * dataProvider.supportsIdentifier(DataProvider.IdentifierType.Static, "rri_weights") >> { return true }
    1 * dataProvider.getStaticData("rri_weights", [:]) >> {
      return """
InstanceType,Region,Weight
r4.2xlarge,us-west-2,0.5
r4.8xlarge,us-west-2,0.5
m5.2xlarge,us-west-2,1.0
""".trim()
    }

    reservations["us-west-2:a:r4.2xlarge:linux"][0].totalSurplus() == 83  // 166 fxlarge
    reservations["us-west-2:b:r4.2xlarge:linux"][0].totalSurplus() == 83  // 166 fxlarge
    reservations["us-west-2:c:r4.2xlarge:linux"][0].totalSurplus() == 83  // 166 fxlarge
    reservations["us-west-2:a:r4.8xlarge:linux"][0].totalSurplus() == 62  // 496 fxlarge
                                                                          // -----------
    reservations["us-west-2:*:r4.fxlarge:linux"][0].totalSurplus() == 6   // 994 fxlarge (1000 - 994 = 6)

    reservations["us-west-2:a:m4.2xlarge:linux"][0].totalSurplus() == 100 // untouched

    reservations["us-west-2:a:m5.2xlarge:linux"][0].totalSurplus() == 0   // not enough surplus for even one instance
    reservations["us-west-2:*:m5.fxlarge:linux"][0].totalSurplus() == 1   // no surplus used

  }

  private AmazonReservationReport.OverallReservationDetail overallReservationDetail(String instanceType,
                                                                                    int totalReserved,
                                                                                    int totalUsed,
                                                                                    String region,
                                                                                    String availabilityZone) {
    return new AmazonReservationReport.OverallReservationDetail(
      totalReserved: new AtomicInteger(totalReserved),
      totalUsed: new AtomicInteger(totalUsed),
      instanceType: instanceType,
      region: region,
      availabilityZone: availabilityZone,
      os: AmazonReservationReport.OperatingSystemType.LINUX
    )
  }
}
