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

import com.netflix.kayenta.judge.detectors.{IQRDetector, KSigmaDetector}
import org.scalatest.FunSuite


class DetectorSuite extends FunSuite{

  test("KSigma Detection"){
    val testData = Array(1.0, 1.0, 1.0, 1.0, 1.0, 20.0, 1.0, 1.0, 1.0, 1.0, 1.0)
    val truth = Array(false, false, false, false, false, true, false, false, false, false, false)

    val detector = new KSigmaDetector(k = 3.0)
    val result = detector.detect(testData)
    assert(result === truth)
  }

  test("KSigma Two Sided"){

    val testData = Array(1.0, 1.0, 1.0, 5.0, -1.0, -1.0, -1.0, -5.0)
    val truth = Array(false, false, false, true, false, false, false, true)

    val detector = new KSigmaDetector(1.0)
    val result = detector.detect(testData)
    assert(result === truth)
  }

  test("IQR Detection"){
    val testData = Array(21.0, 23.0, 24.0, 25.0, 50.0, 29.0, 23.0, 21.0)
    val truth = Array(false, false, false, false, true, false, false, false)

    val detector = new IQRDetector(factor = 1.5)
    val result = detector.detect(testData)
    assert(result === truth)
  }

  test("IQR Detect Two Sided"){
    val testData = Array(1.0, 1.0, 1.0, 5.0, -1.0, -1.0, -1.0, -5.0)
    val truth = Array(false, false, false, true, false, false, false, true)

    val detector = new IQRDetector(factor = 1.5)
    val result = detector.detect(testData)
    assert(result === truth)
  }

  test("IQR Reduce Sensitivity"){
    val testData = Array(1.0, 1.0, 1.0, 1.0, 1.0, 20.0, 1.0, 1.0, 1.0, 1.0, 1.0)
    val truth = Array(false, false, false, false, false, false, false, false, false, false, false)

    val detector = new IQRDetector(factor = 3.0, reduceSensitivity=true)
    val result = detector.detect(testData)
    assert(result === truth)
  }

  test("IQR Empty Data"){
    val testData = Array[Double]()
    val truth = Array[Boolean]()

    val detector = new IQRDetector(factor = 1.5)
    val result = detector.detect(testData)
    assert(result === truth)
  }

}
