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
import com.netflix.spinnaker.clouddriver.aws.provider.view.AmazonS3DataProvider
import com.netflix.spinnaker.clouddriver.model.DataProvider
import groovy.util.logging.Slf4j

import java.util.concurrent.atomic.AtomicInteger

interface AmazonReservationReportBuilder {

  @Slf4j
  class Support {
    // relative multipliers for instance types < xlarge where the baseline is xlarge
    // (would be nice if AWS had an API to derive this from)
    // https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/apply_ri.html
    Map<String, Double> instanceTypeMultipliers = [
      "t2.nano"  : 0.03125,
      "t2.micro" : 0.0625,
      "t2.small" : 0.125,
      "t2.medium": 0.25,
      "t2.large" : 0.5,

      "m1.small" : 0.125,
      "m1.medium": 0.25,
      "m1.large" : 0.5,
      "m3.medium": 0.25,
      "m3.large" : 0.5,
      "m4.large" : 0.5,
      "m5.large" : 0.5,

      "c1.medium": 0.25,
      "c3.large" : 0.5,
      "c4.large" : 0.5,
      "c5.large" : 0.5,

      "r3.large" : 0.5,
      "r4.large" : 0.5,

      "i3.large" : 0.5
    ]

    List<OverallReservationDetail> aggregateRegionalReservations(List<OverallReservationDetail> reservations) {
      def regionalReservations = filterRegionalReservations(reservations)

      regionalReservations.groupBy { "${it.region}-${it.instanceFamily()}-${it.os}" }.collect {
        def overallRegionalReservationDetail = new OverallReservationDetail(
          availabilityZone: "*",
          instanceType: it.value[0].instanceFamily() + ".fxlarge",
          os: it.value[0].os,
          region: it.value[0].region,
        )

        // aggregate all reserved instances in the same family
        it.value.each { OverallReservationDetail o ->
          double multiplier = getMultiplier(o.instanceType)
          if (!multiplier) {
            log.warn("Unable to determine multiplier for instance type '${o.instanceType}'")
            return
          }

          if (multiplier >= 1) {
            overallRegionalReservationDetail.totalReserved.addAndGet(
              (int) multiplier * o.totalSurplus()
            )

            o.totalUsed.set(o.totalSurplus())
          } else {
            // how many instances are required to make a single xlarge
            int convertibleInstances = o.totalSurplus() * multiplier

            overallRegionalReservationDetail.totalReserved.addAndGet(convertibleInstances)
            o.totalUsed.addAndGet((int) (convertibleInstances / multiplier))
          }

          // provide some traceability around which accounts/instanceTypes were converted to xlarge
          o.accounts.each {
            def accountInstanceTypeKey = "${it.key}__${o.instanceType}".toString()
            def accountReservationDetail = overallRegionalReservationDetail.accounts.get(accountInstanceTypeKey)
            accountReservationDetail = accountReservationDetail ?: new AmazonReservationReport.AccountReservationDetail()

            overallRegionalReservationDetail.accounts.put(accountInstanceTypeKey, accountReservationDetail)

            // only consider vpc reservations
            accountReservationDetail.reservedVpc.addAndGet(it.value.reservedVpc.get())
          }
        }

        return overallRegionalReservationDetail
      }
    }

    /**
     * @return all regional reservations (availabilityZone == '*' and supported instance type)
     */
    private List<OverallReservationDetail> filterRegionalReservations(List<OverallReservationDetail> reservations) {
      return reservations.findAll {
        it.availabilityZone == '*' && (it.instanceType.endsWith("xlarge") || instanceTypeMultipliers.containsKey(it.instanceType))
      }
    }

    /**
     * @return the multiplier of a given instanceType related to an 'xlarge', or 0 if instanceType is not supported
     */
    double getMultiplier(String instanceType) {
      if (!instanceTypeMultipliers.containsKey(instanceType) && !instanceType.endsWith("xlarge")) {
        // not an xlarge instance type (or no explicit multiplier if < xlarge)
        return 0
      }

      if (instanceTypeMultipliers.containsKey(instanceType)) {
        return instanceTypeMultipliers.get(instanceType)
      }

      String instanceFamily = instanceType.substring(0, instanceType.indexOf("."))
      return Integer.valueOf(
        instanceType.replaceAll("xlarge", "").replaceAll(instanceFamily + ".", "") ?: "1"
      )
    }
  }

  /**
   * This version (v3) of the reservation report:
   *
   * - normalizes all regional reservations to `fxlarge` instance types
   *   (fxlarge is equivalent to xlarge but kept separate to prevent confusion with normal xlarge instances)
   * - attempts to cover all shortfalls with a subset of regional reservations (converting `xlarge` to the necessary instance types)
   *
   * The subset of regional reservations used to cover is proportional to the shortfall.
   *
   * ie)
   * us-west-2 r3 has a total shortfall of 2000 fxlarge
   * us-west-2a r3.4xlarge has a shortfall of 200 fxlarge (50 r3.4xlarge)
   * us-west-2a r3.4xlarge contributes 10% of the total shortfall (200 of 2000)
   *
   * us-west-2 r3 has a total of 1500 regional fxlarge reservations
   * us-west-2a re.4xlarge would be covered with 150 regional reservations (10% of 1500)
   */
  @Slf4j
  class V3 implements AmazonReservationReportBuilder {
    private final Support support = new Support()

    AmazonReservationReport build(AmazonReservationReport source) {
      def reservations = source.reservations.sort(
        false, new AmazonReservationReport.DescendingOverallReservationDetailComparator()
      )

      // aggregate all regional reservations for each instance family
      def regionalReservations = support.aggregateRegionalReservations(reservations)

      // remove any regional reservations that have been fully utilized (ie. they've all been converted to xlarge)
      reservations.removeAll { it.availabilityZone == "*" }

      // add the aggregated regional reservations
      reservations.addAll(regionalReservations)

      Map<String, List<OverallReservationDetail>> allShortfallsByInstanceFamily = reservations
        .findAll { it.totalSurplus() < 0 }
        .groupBy { "${it.region()}-${it.instanceFamily()}-${it.os.name}".toString() }

      // used to track the order in which regional reservations are used to cover shortfalls
      def allocationIndex = new AtomicInteger(0)
      allShortfallsByInstanceFamily.each { key, value ->
        def regionalReservationForInstanceFamily = regionalReservations.find {
          it.instanceFamily() == value[0].instanceFamily() && value[0].region == it.region && it.os == value[0].os
        }

        if (!regionalReservationForInstanceFamily) {
          // no regional reservations to cover shortfall from
          return
        }

        // total shortfall represented as fxlarge
        def totalShortfallForInstanceFamily = Math.abs((int) value.sum { OverallReservationDetail detail ->
          detail.totalSurplus() * support.getMultiplier(detail.instanceType)
        })

        // track original value as `regionalReservationForInstanceFamily.totalSurplus()` will change
        // when shortfalls are covered
        def totalRegionalReservationsForInstanceFamily = regionalReservationForInstanceFamily.totalSurplus()

        value.each { OverallReservationDetail shortfall ->
          coverShortfall(
            allocationIndex,
            regionalReservationForInstanceFamily,
            totalRegionalReservationsForInstanceFamily,
            shortfall,
            totalShortfallForInstanceFamily
          )
        }
      }

      return new AmazonReservationReport(
        start: source.start,
        end: source.end,
        accounts: source.accounts,
        reservations: reservations,
        errorsByRegion: source.errorsByRegion
      )
    }

    private void coverShortfall(AtomicInteger allocationIndex,
                                OverallReservationDetail regional,
                                int totalRegionalSurplusForFamily,
                                OverallReservationDetail shortfall,
                                int totalShortfallForFamily) {
      def multiplier = support.getMultiplier(shortfall.instanceType)
      if (!multiplier) {
        // instance type is unsupported (some unknown variant smaller than an xlarge)
        log.warn("Unable to determine multiplier for instance type '${shortfall.instanceType}'")
        return
      }

      // Math.ceil() protects against fractional instances when going from xlarge -> large (0.5 multiplier)
      int sourceInstancesNeeded = Math.ceil(Math.abs(shortfall.totalSurplus() * multiplier))

      def percentageOfShortfall = Math.abs((double) sourceInstancesNeeded / (double) totalShortfallForFamily)
      def portionOfAvailableSurplus = (int) Math.floor(totalRegionalSurplusForFamily * percentageOfShortfall)

      if (portionOfAvailableSurplus >= sourceInstancesNeeded) {
        // we have more instances than necessary to cover the shortfall
        int targetInstanceQuantity = sourceInstancesNeeded / multiplier

        regional.totalRegionalReserved.addAndGet(-1 * sourceInstancesNeeded)
        shortfall.totalRegionalReserved.addAndGet(targetInstanceQuantity)

        def allocation = new AmazonReservationReport.Allocation(
          source: regional.id(),
          sourceInstanceQuantity: sourceInstancesNeeded,
          sourceInstanceType: regional.instanceType,
          target: shortfall.id(),
          targetInstanceQuantity: targetInstanceQuantity,
          targetInstanceType: shortfall.instanceType,
          index: allocationIndex.incrementAndGet(),

          totalRegionalSurplusForFamily : totalRegionalSurplusForFamily,
          totalShortfallForFamily: totalShortfallForFamily,
          percentageOfShortfall: percentageOfShortfall,
          portionOfAvailableSurplus: portionOfAvailableSurplus
        )

        regional.regionalReservedAllocations << allocation
        shortfall.regionalReservedAllocations << allocation
      } else {
        // determine how much shortfall can be covered as surplus not large enough to cover everything
        // (may not be entirety of surplus depending on multiplier)
        int targetInstanceQuantity = Math.floor(portionOfAvailableSurplus / multiplier)

        if (targetInstanceQuantity > 0) {
          regional.totalRegionalReserved.addAndGet(-1 * (int) (targetInstanceQuantity * multiplier))
          shortfall.totalRegionalReserved.addAndGet(targetInstanceQuantity)

          def allocation = new AmazonReservationReport.Allocation(
            source: regional.id(),
            sourceInstanceQuantity: targetInstanceQuantity * multiplier,
            sourceInstanceType: regional.instanceType,
            target: shortfall.id(),
            targetInstanceQuantity: targetInstanceQuantity,
            targetInstanceType: shortfall.instanceType,
            index: allocationIndex.incrementAndGet(),

            totalRegionalSurplusForFamily : totalRegionalSurplusForFamily,
            totalShortfallForFamily: totalShortfallForFamily,
            percentageOfShortfall: percentageOfShortfall,
            portionOfAvailableSurplus: portionOfAvailableSurplus
          )

          regional.regionalReservedAllocations << allocation
          shortfall.regionalReservedAllocations << allocation
        }
      }
    }
  }

  /**
   * This version (v4) of the reservation builds upon v3 by:
   *
   * - allowing an external source (file in s3) to specify the ratio that surplus regional reservations should be
   *   shared with their AZ equivalents.
   *
   * This additional sharing occurs after all instance family shortfalls have been covered.
   *
   *
   * ie)
   * us-west-2 r3 has a total of 1500 surplus fxlarge reservations
   * r3.2xlarge should get 25%
   * r3.4xlarge should get 25%
   * r3.8xlarge should get 50%
   *
   * assuming 3 availability zones:
   * us-west-2a r3.2xlarge would receive 125 fxlarge (62 * 2xlarge)
   * us-west-2b r3.2xlarge would receive 125 fxlarge (62 * 2xlarge)
   * us-west-2c r3.2xlarge would receive 125 fxlarge (62 * 2xlarge)
   *
   * us-west-2a r3.4xlarge would receive 125 fxlarge (31 * 4xlarge)
   * us-west-2b r3.4xlarge would receive 125 fxlarge (31 * 4xlarge)
   * us-west-2c r3.4xlarge would receive 125 fxlarge (31 * 4xlarge)
   *
   * us-west-2a r3.8xlarge would receive 250 fxlarge (31 * 8xlarge)
   * us-west-2b r3.8xlarge would receive 250 fxlarge (31 * 8xlarge)
   * us-west-2c r3.8xlarge would receive 250 fxlarge (31 * 8xlarge)
   */
  @Slf4j
  class V4 implements AmazonReservationReportBuilder {
    private final Support support = new Support()

    AmazonReservationReport build(AmazonS3DataProvider dataProvider,
                                  AmazonReservationReport source) {
      // this is very particular to an internal Netflix implementation
      if (!dataProvider.supportsIdentifier(DataProvider.IdentifierType.Static, "rri_weights")) {
        return source
      }

      def reservations = source.reservations

      /**
       * InstanceType,Region,Weight
       * m5.large,us-east-1,0
       * m5.xlarge,us-east-1,0.25
       * m5.2xlarge,us-east-1,0.25
       * m5.4xlarge,us-east-1,0.25
       * m5.12xlarge,us-east-1,0.25
       * m5.24xlarge,us-east-1,0
       * ...
       */
      def rriWeights = dataProvider.getStaticData("rri_weights", [:]) as String
      def lines = rriWeights.split("\n") as List<String>

      def regionalReservationWeights = lines.subList(1, lines.size()).collect {
        def splits = it.split(",")
        return new RegionalReservationWeight(
          instanceType: splits[0],
          region: splits[1],
          weight: Double.valueOf(splits[2])
        )
      }.findAll { it.weight > 0 }

      // track _original_ surplus as it will change as allocations are made az-by-az
      def originalFinancialSurpluses = reservations.findAll {
        it.totalSurplus() > 0 && it.instanceType.endsWith("fxlarge")
      }.collectEntries {
        [ it.id(), it.totalSurplus() ]
      }

      def allocationIndex = new AtomicInteger(1000000)
      regionalReservationWeights.each { RegionalReservationWeight weight ->
        def financialReservations = reservations.findAll {
          it.totalSurplus() > 0 &&
          it.region() == weight.region &&
          it.instanceType == weight.instanceFamily() + ".fxlarge"
        }

        financialReservations.each { OverallReservationDetail financialReservation ->
          // financial reservations will exist for a given instance type/region across multiple operating system types
          def azReservations = reservations.findAll {
            it.region() == financialReservation.region &&
            it.instanceType == weight.instanceType &&
            it.os == financialReservation.os
          }

          def availableFinancialReservations = originalFinancialSurpluses[financialReservation.id()] * weight.weight
          def availableFinancialReservationsPerAZ =  availableFinancialReservations / azReservations.size()
          azReservations.each { OverallReservationDetail azReservation ->
            int targetInstanceQuantity
            int sourceInstanceQuantity

            def multiplier = support.getMultiplier(azReservation.instanceType)
            if (multiplier > 1) {
              targetInstanceQuantity = availableFinancialReservationsPerAZ / multiplier
              sourceInstanceQuantity = targetInstanceQuantity * multiplier
            } else {
              targetInstanceQuantity = availableFinancialReservationsPerAZ * multiplier
              sourceInstanceQuantity = targetInstanceQuantity / multiplier
            }

            if (sourceInstanceQuantity > 0) {
              financialReservation.totalRegionalReserved.addAndGet(-1 * sourceInstanceQuantity)
              azReservation.totalRegionalReserved.addAndGet(targetInstanceQuantity)

              def allocation = new AmazonReservationReport.Allocation(
                source: financialReservation.id(),
                sourceInstanceQuantity: targetInstanceQuantity * multiplier,
                sourceInstanceType: financialReservation.instanceType,
                target: azReservation.id(),
                targetInstanceQuantity: targetInstanceQuantity,
                targetInstanceType: azReservation.instanceType,
                index: allocationIndex.incrementAndGet(),
              )

              financialReservation.regionalReservedAllocations << allocation
              azReservation.regionalReservedAllocations << allocation
            }
          }
        }
      }

      return source
    }

    private class RegionalReservationWeight {
      String instanceType
      String region
      Double weight

      String instanceFamily() {
        return instanceType.substring(0, 2)
      }
    }

  }
}
