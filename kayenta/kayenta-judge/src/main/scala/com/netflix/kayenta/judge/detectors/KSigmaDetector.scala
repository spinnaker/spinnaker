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

package com.netflix.kayenta.judge.detectors

import org.apache.commons.math3.stat.StatUtils

/**
  * KSigma Detector
  *
  * Values which are greater than or less than k standard deviations from the mean are considered outliers
  * Reference: https://en.wikipedia.org/wiki/68%E2%80%9395%E2%80%9399.7_rule
  */
class KSigmaDetector(k: Double = 3.0) extends BaseOutlierDetector{

  require(k > 0.0, "k must be greater than zero")

  override def detect(data: Array[Double]): Array[Boolean] = {

    //Calculate the mean and standard deviation of the input data
    val mean = StatUtils.mean(data)
    val variance = StatUtils.populationVariance(data)
    val stdDeviation = scala.math.sqrt(variance)

    //Values that fall outside of k standard deviations from the mean are considered outliers
    data.map(x => if (scala.math.abs(x - mean) > (stdDeviation * k)) true else false)
  }
}
