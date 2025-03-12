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

  test("IQR Empty Data"){
    val testData = Array[Double]()
    val truth = Array[Boolean]()

    val detector = new IQRDetector(factor = 1.5)
    val result = detector.detect(testData)
    assert(result === truth)
  }

  test("IQR NIST Test"){
    val testData = Array[Double](
      30, 171, 184, 201, 212, 250, 265, 270, 272, 289, 305, 306, 322, 322, 336, 346,
      351, 370, 390, 404, 409, 411, 436, 437, 439, 441, 444, 448, 451, 453, 470, 480,
      482, 487, 494, 495, 499, 503, 514, 521, 522, 527, 548, 550, 559, 560, 570, 572,
      574, 578, 585, 592, 592, 607, 616, 618, 621, 629, 637, 638, 640, 656, 668, 707,
      709, 719, 737, 739, 752, 758, 766, 792, 792, 794, 802, 818, 830, 832, 843, 858,
      860, 869, 918, 925, 953, 991, 1000, 1005, 1068, 1441
    )
    val truth = Array.fill[Boolean](testData.length - 1)(false) :+ true

    val detector = new IQRDetector(factor = 1.5)
    val result = detector.detect(testData)
    assert(result === truth)
  }

}
