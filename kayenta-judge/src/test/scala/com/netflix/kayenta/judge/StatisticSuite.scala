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

import com.netflix.kayenta.judge.stats.{DescriptiveStatistics, MetricStatistics}
import com.netflix.kayenta.judge.stats.DescriptiveStatistics.percentile
import org.scalatest.FunSuite
import org.scalatest.Matchers._

class StatisticSuite extends FunSuite{

  test("Summary Statistics"){
    val metric = Metric("test", Array[Double](1.0), "test")
    val result = DescriptiveStatistics.summary(metric)
    val truth = MetricStatistics(1, 1, 1, 1, 1)
    assert(result === truth)
  }

  test("Summary Statistics No Data"){
    val metric = Metric("testNoData", Array[Double](), "test")
    val result = DescriptiveStatistics.summary(metric)
    val truth = MetricStatistics(0, 0, 0, 0, 0)
    assert(result === truth)
  }

  test("Basic Percentile Test"){
    val testData = Array(0.0, 0.5, 1.0, 1.5, 2.0, 2.5, 3.0, 3.5)
    assert(percentile(testData, 5) === (0.175 +- 1.0e-4))
    assert(percentile(testData, 50) === 1.75)
    assert(percentile(testData, 100) === 3.5)
  }

  test("Basic Percentile Estimate Test (Linear Interpolation)") {
    val testData = Array(0.0, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0)
    assert(percentile(testData, 50) === 4.5)
  }

  test("Percentile NIST Test"){
    val testData = Array(
      95.1772, 95.1567, 95.1937, 95.1959, 95.1442, 95.0610,
      95.1591, 95.1195, 95.1772, 95.0925, 95.1990, 95.1682
    )
    assert(percentile(testData, 90) === 95.19568)
  }

  test("Percentile Metric Object Test"){
    val metric = Metric("test", Array[Double](1.0), "test")
    assert(percentile(metric, 100) === 1.0)
  }

  test("Percentile Estimate Test (Linear Interpolation)"){
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

}
