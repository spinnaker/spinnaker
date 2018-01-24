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

import com.netflix.kayenta.judge.preprocessing.Transforms.{removeNaNs, removeOutliers}
import com.netflix.kayenta.judge.detectors.{IQRDetector, KSigmaDetector}
import com.netflix.kayenta.judge.preprocessing.Transforms
import org.scalatest.FunSuite


class TransformSuite extends FunSuite{

  test("Remove Single NaN"){
    val testData = Array(0.0, 1.0, Double.NaN, 1.0, 0.0)
    val truth = Array(0.0, 1.0, 1.0, 0.0)

    val result = Transforms.removeNaNs(testData)
    assert(result === truth)
  }

  test("Remove Multiple NaN"){
    val testData = Array(Double.NaN, Double.NaN, Double.NaN)
    val truth = Array[Double]()

    val result = removeNaNs(testData)
    assert(result === truth)
  }

  test("IQR Outlier Removal"){
    val testData = Array(21.0, 23.0, 24.0, 25.0, 50.0, 29.0, 23.0, 21.0)
    val truth = Array(21.0, 23.0, 24.0, 25.0, 29.0, 23.0, 21.0)

    val detector = new IQRDetector(factor = 1.5)
    val result = removeOutliers(testData, detector)
    assert(result === truth)
  }

  test("KSigma Outlier Removal"){
    val testData = Array(1.0, 1.0, 1.0, 1.0, 1.0, 20.0, 1.0, 1.0, 1.0, 1.0, 1.0)
    val truth = Array(1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0)

    val detector = new KSigmaDetector(k = 3.0)
    val result = removeOutliers(testData, detector)
    assert(result === truth)
  }

}
