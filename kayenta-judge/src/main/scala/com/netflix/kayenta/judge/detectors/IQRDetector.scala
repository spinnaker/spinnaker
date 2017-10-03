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

import com.netflix.kayenta.judge.stats.DescriptiveStatistics.percentile

/**
  * Interquartile Range Detector
  *
  * Values which fall below Q1-factor*(IQR) or above Q3+factor(IQR) are considered outliers.
  * The IQR is a measure of statistical dispersion, being equal to the difference between
  * the upper and lower quartiles.
  *
  * Reference: https://en.wikipedia.org/wiki/Outlier#Tukey.27s_test
  * Note: To reduce sensitivity, take the max of the IQR or the 99th percentile
  */
class IQRDetector(factor: Double = 1.5, reduceSensitivity: Boolean = false) extends OutlierDetector {

  require(factor > 0.0, "factor must be greater than zero")

  /**
    * Calculate the Interquartile Range (IQR)
    * @param data
    * @return
    */
  private def calculateIQR(data: Array[Double]): (Double, Double) = {
    //Calculate the 25th and 75th percentiles
    val p75 = percentile(data, 75)
    val p25 = percentile(data, 25)

    //Calculate the Interquartile Range (IQR)
    val iqr = p75-p25

    //Calculate the upper and lower fences
    val lowerIQR = p25 - (factor * iqr)
    val upperIQR = p75 + (factor * iqr)

    (lowerIQR, upperIQR)
  }

  override def detect(data: Array[Double]): Array[Boolean] ={

    val (lowerFence, upperFence) = if(reduceSensitivity){

      //Calculate the Interquartile Range (IQR)
      val (lowerIQR, upperIQR) = calculateIQR(data)

      //Calculate the 1st and 99th percentiles
      val p01 = percentile(data, 1)
      val p99 = percentile(data, 99)

      //Calculate the upper and lower fences
      val lowerFence = math.min(p01, lowerIQR)
      val upperFence = math.max(p99, upperIQR)
      (lowerFence, upperFence)

    } else {
      calculateIQR(data)
    }

    data.map(x => if (x > upperFence || x < lowerFence) true else false)
  }
}