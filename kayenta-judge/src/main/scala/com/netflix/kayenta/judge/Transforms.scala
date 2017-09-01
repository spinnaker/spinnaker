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

import com.netflix.kayenta.judge.detectors.OutlierDetector


object Transforms {

  /**
    * Remove NaN values from the input array
    * @param data
    * @return
    */
  def removeNaNs(data: Array[Double]): Array[Double] ={
    data.filter(x => !x.isNaN)
  }

  /**
    * Remove NaN values from the input metric
    * @param metric
    */
  def removeNaNs(metric: Metric): Metric = {
    metric.copy(values = removeNaNs(metric.values))
  }

  /**
    * Replace NaN values from the input array
    * @param data
    * @param value
    * @return
    */
  def replaceNaNs(data: Array[Double], value: Double): Array[Double]={
    data.map(x => if(x.isNaN) value else x)
  }

  /**
    * Remove outliers from the input array
    * @param data
    * @param detector
    */
  def removeOutliers(data: Array[Double], detector: OutlierDetector): Array[Double] ={
    val outliers = detector.detect(data)
    data.zip(outliers).collect{case (v, false) => v}
  }

  /**
    * Remove outliers from the input metric
    * @param metric
    * @param detector
    * @return
    */
  def removeOutliers(metric: Metric, detector: OutlierDetector): Metric = {
    metric.copy(values = removeOutliers(metric.values, detector))
  }

}
