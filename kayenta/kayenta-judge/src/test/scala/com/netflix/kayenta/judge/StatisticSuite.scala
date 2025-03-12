/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.kayenta.judge

import com.netflix.kayenta.judge.stats.DescriptiveStatistics.percentile
import com.netflix.kayenta.judge.stats.{DescriptiveStatistics, EffectSizes, MetricStatistics}
import org.scalatest.FunSuite
import org.scalatest.Matchers._

class StatisticSuite extends FunSuite{

  test("Summary Statistics: Scalar"){
    val metric = Metric("scalar", Array[Double](4.0), "test")
    val result = DescriptiveStatistics.summary(metric)
    val truth = MetricStatistics(4, 4, 4, 0, 1)
    assert(result === truth)
  }

  test("Summary Statistics: List"){
    val metric = Metric("list", Array[Double](1.0, 1.0, 1.0, 10.0, 10.0, 10.0), "test")
    val result = DescriptiveStatistics.summary(metric)

    assert(result.min === 1.0)
    assert(result.max === 10.0)
    assert(result.mean === 5.5)
    assert(result.std === (4.9295 +- 1e-4))
    assert(result.count === 6.0)
  }

  test("Summary Statistics: No Data"){
    val metric = Metric("testNoData", Array[Double](), "test")
    val result = DescriptiveStatistics.summary(metric)
    val truth = MetricStatistics(0, 0, 0, 0, 0)
    assert(result === truth)
  }

  test("Summary Statistics: Basic Percentile"){
    val testData = Array(0.0, 0.5, 1.0, 1.5, 2.0, 2.5, 3.0, 3.5)
    assert(percentile(testData, 5) === (0.175 +- 1.0e-4))
    assert(percentile(testData, 50) === 1.75)
    assert(percentile(testData, 100) === 3.5)
  }

  test("Summary Statistics: Basic Percentile Estimate (Linear Interpolation)") {
    val testData = Array(0.0, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0)
    assert(percentile(testData, 50) === 4.5)
  }

  test("Summary Statistics: Percentile (NIST Data)"){
    val testData = Array(
      95.1772, 95.1567, 95.1937, 95.1959, 95.1442, 95.0610,
      95.1591, 95.1195, 95.1772, 95.0925, 95.1990, 95.1682
    )
    assert(percentile(testData, 90) === 95.19568)
  }

  test("Summary Statistics: Percentile Metric Object"){
    val metric = Metric("test", Array[Double](1.0), "test")
    assert(percentile(metric, 100) === 1.0)
  }

  test("Summary Statistics: Percentile Estimate (Linear Interpolation)"){
    val testData = Array(
      0.07142857142857144, 0.02083333333333332, 0.16666666666666666,
      0.03448275862068966, 0.038461538461538464, 0.03225806451612904,
      0.027777777777777773, 0.0, 0.23076923076923078, 0.10344827586206898,
      0.04545454545454542, 0.0, 0.028571428571428564, 0.0, 0.0, 0.04, 0.0, 0.0,
      0.05128205128205127, 0.10714285714285716, 0.0263157894736842,
      0.04166666666666667, 0.09523809523809522, 0.02941176470588235,
      0.024999999999999984, 0.0, 0.0, 0.023809523809523794, 0.0,
      0.02564102564102563, 0.0, 0.0, 0.028571428571428564, 0.07142857142857144,
      0.047619047619047596, 0.021276595744680833, 0.02564102564102563, 0.03125,
      0.03125, 0.03125, 0.11363636363636356, 0.03571428571428572, 0.0,
      0.02777777777777777, 0.0, 0.0, 0.055555555555555546, 0.028571428571428564,
      0.03225806451612904
    )
    assert(percentile(testData, 25) === 0.0)
    assert(percentile(testData, 75) === (0.0416 +- 1.0e-4))
  }

  test("Effect Size: Mean Ratio (No Effect)"){
    val experimentData = Array(1.0, 1.0, 1.0, 1.0, 1.0)
    val controlData = Array(1.0, 1.0, 1.0, 1.0, 1.0)

    val experimentMetric = Metric("test-metric", experimentData, "canary")
    val controlMetric = Metric("test-metric", controlData, "baseline")

    val result = EffectSizes.meanRatio(controlMetric, experimentMetric)
    assert(result === 1.0)
  }

  test("Effect Size: Mean Ratio (Summary Stats)"){
    val experimentData = Array(1.0, 1.0, 1.0, 1.0, 1.0)
    val controlData = Array(1.0, 1.0, 1.0, 1.0, 1.0)

    val experimentMetric = Metric("test-metric", experimentData, "canary")
    val controlMetric = Metric("test-metric", controlData, "baseline")

    val experimentStats = DescriptiveStatistics.summary(experimentMetric)
    val controlStats = DescriptiveStatistics.summary(controlMetric)

    val result = EffectSizes.meanRatio(controlStats, experimentStats)
    assert(result === 1.0)
  }

  test("Effect Size: Mean Ratio (High)"){
    val experimentData = Array(10.0, 10.0, 10.0, 10.0, 10.0)
    val controlData = Array(1.0, 1.0, 1.0, 1.0, 1.0)

    val experimentMetric = Metric("high-metric", experimentData, "canary")
    val controlMetric = Metric("high-metric", controlData, "baseline")

    val result = EffectSizes.meanRatio(controlMetric, experimentMetric)
    assert(result === 10.0)
  }

  test("Effect Size: Mean Ratio (Low)"){
    val experimentData = Array(10.0, 10.0, 10.0, 10.0, 10.0)
    val controlData = Array(100.0, 100.0, 100.0, 100.0, 100.0)

    val experimentMetric = Metric("high-metric", experimentData, "canary")
    val controlMetric = Metric("high-metric", controlData, "baseline")

    val result = EffectSizes.meanRatio(controlMetric, experimentMetric)
    assert(result === 0.1)
  }

  test("Effect Size: Mean Ratio (Zero Mean)"){
    val experimentData = Array(10.0, 10.0, 10.0, 10.0, 10.0)
    val controlData = Array(0.0, 0.0, 0.0, 0.0, 0.0)

    val experimentMetric = Metric("high-metric", experimentData, "canary")
    val controlMetric = Metric("high-metric", controlData, "baseline")

    val result = EffectSizes.meanRatio(controlMetric, experimentMetric)
    assert(result.isNaN)
  }

  test("Effect Size: Cohen's D"){
    val experimentData = Array(5.0, 5.0, 5.0, 10.0, 10.0, 10.0)
    val controlData = Array(1.0, 1.0, 1.0, 2.0, 2.0, 2.0)

    val experimentMetric = Metric("high-metric", experimentData, "canary")
    val controlMetric = Metric("high-metric", controlData, "baseline")

    val result = EffectSizes.cohenD(experimentMetric, controlMetric)
    assert(result === (3.03821 +- 1e-5))
  }

  test("CLES Effect Size: Identical"){
    val experimentData = Array(1.0, 2.0, 3.0, 4.0, 5.0)
    val controlData =  Array(1.0, 2.0, 3.0, 4.0, 5.0)

    val experimentMetric = Metric("test-metric", experimentData, "canary")
    val controlMetric = Metric("test-metric", controlData, "baseline")

    val result = EffectSizes.cles(controlMetric, experimentMetric)
    assert(result === 0.5)
  }

  test("CLES Effect Size: Different (No Overlap)"){
    val experimentData = Array(
      2.27776335, 2.61149434, 3.26894105, 2.91672701, 1.40656921,
      2.18292082, 3.47901292, 3.00034118, 3.02402043, 4.69912745
    )
    val controlData =  Array(
      25.31531073, 25.19450817, 24.6288846 , 24.37991861, 26.40999244,
      25.28524759, 24.99788745, 26.13841676, 22.93604649, 24.56817832
    )

    val experimentMetric = Metric("test-metric", experimentData, "canary")
    val controlMetric = Metric("test-metric", controlData, "baseline")

    val result = EffectSizes.cles(controlMetric, experimentMetric)
    assert(result === 0.0)
  }

  test("CLES Effect Size: High Metric"){
    val experimentData = Array(
      29.17644856, 25.16947646, 25.88467948, 29.4900454 , 27.05622757,
      26.13307864, 27.2711558 , 26.69139944, 29.05412377, 26.39818697,
      26.68312641, 24.99600187, 27.99524816, 22.57278291, 23.72798196,
      25.82821505, 28.26624521, 28.02370228, 24.1792213 , 26.02914828,
      25.90970275, 31.18337461, 27.44254942, 29.93347686, 26.70848069,
      28.12191913, 27.39257837, 30.17636627, 29.60240485, 30.10635388
    )
    val controlData =  Array(
      24.69397578, 25.37120574, 25.86186148, 26.93804252, 22.72232309,
      24.15894084, 24.80842752, 26.07830794, 27.35323988, 23.25110886,
      24.65909111, 23.88299016, 21.34817847, 26.33198082, 23.39498829,
      26.6734461 , 25.33769589, 23.92348759, 25.29740715, 24.21381887,
      28.1697053 , 22.2271772 , 27.16612837, 25.16639243, 26.85955896,
      27.13421926, 25.43350074, 26.32130314, 23.8854011 , 27.86180318
    )

    val experimentMetric = Metric("high-metric", experimentData, "canary")
    val controlMetric = Metric("high-metric", controlData, "baseline")

    val result = EffectSizes.cles(controlMetric, experimentMetric)
    assert(result === (0.771 +- 0.001))
  }

  test("CLES Effect Size: Low Metric"){
    val experimentData = Array(
      22.58442682, 18.83161858, 22.34282944, 18.52177725, 19.41399147,
      21.77120485, 24.33796385, 22.16390531, 23.781802  , 21.38514296,
      22.2205035 , 22.06152361, 21.30313392, 21.59652134, 22.38394223,
      24.68882792, 20.58916439, 21.03137302, 22.26213957, 23.76485986,
      20.56271226, 24.56567102, 19.86055958, 22.18967677, 20.31475409,
      19.2634656 , 24.60283585, 20.41729998, 20.62069798, 22.53448767
    )
    val controlData =  Array(
      23.15322873, 22.09179077, 26.56410603, 23.0971542 , 24.26372276,
      25.6461273 , 29.42730472, 22.08630097, 29.15257054, 25.8096766 ,
      24.62191338, 23.67398895, 23.63946085, 26.44684325, 25.76230914,
      25.41279862, 22.64005862, 24.58898152, 25.01968457, 25.03695883,
      23.79864705, 26.60180151, 25.99405034, 22.46726084, 28.31843786,
      24.30152484, 22.83713467, 21.92380184, 23.44735223, 29.15676219
    )

    val experimentMetric = Metric("low-metric", experimentData, "canary")
    val controlMetric = Metric("low-metric", controlData, "baseline")

    val result = EffectSizes.cles(controlMetric, experimentMetric)
    assert(result === 0.12)
  }
}
