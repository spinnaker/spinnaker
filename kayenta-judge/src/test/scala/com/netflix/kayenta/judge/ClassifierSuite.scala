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

import com.netflix.kayenta.judge.classifiers.metric._
import org.scalatest.FunSuite
import org.scalatest.Matchers._


class ClassifierSuite extends FunSuite{

  test("Mann-Whitney Classifier Test: Pass Metric"){

    val experimentData = Array(
      9.7767343120109462, 10.587071850477695, 7.8375506388901766, 9.3243326886113884,
      8.3774505409784421, 8.9359264399606282, 8.2707663324728511, 9.7484308126716925,
      11.623746715547156, 9.2753442679633942, 8.1538047749171518, 10.562020975754294,
      13.498135575451835, 11.054419256110332, 6.9538903590055092, 12.521008342195479,
      9.0216358603546443, 4.9234856699531662, 11.251360203607172, 11.598449508040337,
      8.435232204668182, 11.110228516867279, 10.70063380164755, 8.9212773652115249,
      10.086167067942661, 11.42107515483087, 9.3792347813644952, 7.7980320148552593,
      10.62339807275746, 11.909642062063739
    )
    val controlData = Array(
      11.1453586774086, 9.9618059885597017, 7.068574306369829, 7.3012661810483657,
      9.7944630265656727, 9.6037918603687302, 6.1867793350339388, 12.345599159550174,
      10.371891733859533, 8.6094300785016724, 9.913162673361084, 10.361375299003466,
      14.206800387518818, 10.946153754753301, 10.215876627044048, 11.739760899484603,
      10.907627468626439, 12.823353694464775, 8.1018609771732137, 8.4981616408804932,
      12.113441374697231, 13.272210809932028, 10.114579381663297, 6.9463958767978067,
      6.2105278541727778, 10.160401684903775, 8.3457712066455461, 8.383822063895833,
      7.0020205070083765, 10.755757401808442
    )

    val experimentMetric = Metric("pass-metric", experimentData, "canary")
    val controlMetric = Metric("pass-metric", controlData, "baseline")

    val classifier = new MannWhitneyClassifier(tolerance = 0.10, confLevel = 0.95)
    val result = classifier.classify(controlMetric, experimentMetric, MetricDirection.Either)

    assert(result.classification == Pass)
  }

  test("Mann-Whitney Classifier Test: High Metric"){

    val experimentData = Array(
      20.878412601720026, 21.78550581191622, 19.70828084717019, 15.859946903461562,
      19.510058772176841, 19.392005525270303, 22.926528314912101, 17.156530276709894,
      18.655117916226171, 23.422607911610392, 24.055817684156644, 22.62689958516501,
      21.979472967817678, 21.659318607218694, 20.067707574072426, 22.119570836305414,
      16.44037132663307, 21.559622826341268, 21.000181443489218, 17.779369411412663,
      17.806006358943584, 18.537200618386109, 20.555550300210797, 19.552928184468378,
      21.771118944087092, 21.809622806176627, 20.961762909905271, 20.977571577503021,
      19.106303205498794, 16.976918966987078
    )
    val controlData = Array(
      11.1453586774086, 9.9618059885597017, 7.068574306369829, 7.3012661810483657,
      9.7944630265656727, 9.6037918603687302, 6.1867793350339388, 12.345599159550174,
      10.371891733859533, 8.6094300785016724, 9.913162673361084, 10.361375299003466,
      14.206800387518818, 10.946153754753301, 10.215876627044048, 11.739760899484603,
      10.907627468626439, 12.823353694464775, 8.1018609771732137, 8.4981616408804932,
      12.113441374697231, 13.272210809932028, 10.114579381663297, 6.9463958767978067,
      6.2105278541727778, 10.160401684903775, 8.3457712066455461, 8.383822063895833,
      7.0020205070083765, 10.755757401808442
    )

    val experimentMetric = Metric("high-metric", experimentData, "canary")
    val controlMetric = Metric("high-metric", controlData, "baseline")

    val classifier = new MannWhitneyClassifier(tolerance = 0.10, confLevel = 0.95)
    val result = classifier.classify(controlMetric, experimentMetric, MetricDirection.Either)

    assert(result.classification == High)
  }

  test("Mann-Whitney Classifier Test: Low Metric"){

    val experimentData = Array(
      9.7767343120109462, 10.587071850477695, 7.8375506388901766, 9.3243326886113884,
      8.3774505409784421, 8.9359264399606282, 8.2707663324728511, 9.7484308126716925,
      11.623746715547156, 9.2753442679633942, 8.1538047749171518, 10.562020975754294,
      13.498135575451835, 11.054419256110332, 6.9538903590055092, 12.521008342195479,
      9.0216358603546443, 4.9234856699531662, 11.251360203607172, 11.598449508040337,
      8.435232204668182, 11.110228516867279, 10.70063380164755, 8.9212773652115249,
      10.086167067942661, 11.42107515483087, 9.3792347813644952, 7.7980320148552593,
      10.62339807275746, 11.909642062063739
    )
    val controlData = Array(
      20.758113589510348, 22.802059629464559, 20.612340363492208, 20.973274654749986,
      23.469237330498622, 21.53133988954805, 21.767353687439805, 18.308050730374301,
      19.062145167827566, 25.879498436001779, 18.982629726336128, 19.387920182269266,
      18.995398251246982, 24.228420230932805, 20.050985172694688, 18.2340451091756,
      17.870515494460435, 23.058103863806618, 18.400259567501134, 16.971352091177167,
      19.467529066052151, 17.90465423342031, 17.553948617825831, 16.104611843907193,
      24.38171081433039, 15.919294339922635, 20.479409626304697, 19.08307201492406,
      17.455788050722866, 18.853826823235142
    )

    val experimentMetric = Metric("low-metric", experimentData, "canary")
    val controlMetric = Metric("low-metric", controlData, "baseline")

    val classifier = new MannWhitneyClassifier(tolerance = 0.10, confLevel = 0.95)
    val result = classifier.classify(controlMetric, experimentMetric, MetricDirection.Either)

    assert(result.classification == Low)
  }

  test("Mann-Whitney Classifier Directional Test: Increase"){

    val experimentData = Array(
      9.7767343120109462, 10.587071850477695, 7.8375506388901766, 9.3243326886113884,
      8.3774505409784421, 8.9359264399606282, 8.2707663324728511, 9.7484308126716925,
      11.623746715547156, 9.2753442679633942, 8.1538047749171518, 10.562020975754294,
      13.498135575451835, 11.054419256110332, 6.9538903590055092, 12.521008342195479,
      9.0216358603546443, 4.9234856699531662, 11.251360203607172, 11.598449508040337,
      8.435232204668182, 11.110228516867279, 10.70063380164755, 8.9212773652115249,
      10.086167067942661, 11.42107515483087, 9.3792347813644952, 7.7980320148552593,
      10.62339807275746, 11.909642062063739
    )
    val controlData = Array(
      20.758113589510348, 22.802059629464559, 20.612340363492208, 20.973274654749986,
      23.469237330498622, 21.53133988954805, 21.767353687439805, 18.308050730374301,
      19.062145167827566, 25.879498436001779, 18.982629726336128, 19.387920182269266,
      18.995398251246982, 24.228420230932805, 20.050985172694688, 18.2340451091756,
      17.870515494460435, 23.058103863806618, 18.400259567501134, 16.971352091177167,
      19.467529066052151, 17.90465423342031, 17.553948617825831, 16.104611843907193,
      24.38171081433039, 15.919294339922635, 20.479409626304697, 19.08307201492406,
      17.455788050722866, 18.853826823235142
    )

    val experimentMetric = Metric("pass-metric", experimentData, "canary")
    val controlMetric = Metric("pass-metric", controlData, "baseline")

    val classifier = new MannWhitneyClassifier(tolerance = 0.10, confLevel = 0.95)
    val result = classifier.classify(controlMetric, experimentMetric, MetricDirection.Increase)

    assert(result.classification == Pass)
  }

  test("Mann-Whitney Classifier Directional Test: Decrease"){

    val experimentData = Array(
      20.878412601720026, 21.78550581191622, 19.70828084717019, 15.859946903461562,
      19.510058772176841, 19.392005525270303, 22.926528314912101, 17.156530276709894,
      18.655117916226171, 23.422607911610392, 24.055817684156644, 22.62689958516501,
      21.979472967817678, 21.659318607218694, 20.067707574072426, 22.119570836305414,
      16.44037132663307, 21.559622826341268, 21.000181443489218, 17.779369411412663,
      17.806006358943584, 18.537200618386109, 20.555550300210797, 19.552928184468378,
      21.771118944087092, 21.809622806176627, 20.961762909905271, 20.977571577503021,
      19.106303205498794, 16.976918966987078
    )
    val controlData = Array(
      11.1453586774086, 9.9618059885597017, 7.068574306369829, 7.3012661810483657,
      9.7944630265656727, 9.6037918603687302, 6.1867793350339388, 12.345599159550174,
      10.371891733859533, 8.6094300785016724, 9.913162673361084, 10.361375299003466,
      14.206800387518818, 10.946153754753301, 10.215876627044048, 11.739760899484603,
      10.907627468626439, 12.823353694464775, 8.1018609771732137, 8.4981616408804932,
      12.113441374697231, 13.272210809932028, 10.114579381663297, 6.9463958767978067,
      6.2105278541727778, 10.160401684903775, 8.3457712066455461, 8.383822063895833,
      7.0020205070083765, 10.755757401808442
    )

    val experimentMetric = Metric("pass-metric", experimentData, "canary")
    val controlMetric = Metric("pass-metric", controlData, "baseline")

    val classifier = new MannWhitneyClassifier(tolerance = 0.10, confLevel = 0.95)
    val result = classifier.classify(controlMetric, experimentMetric, MetricDirection.Decrease)

    assert(result.classification == Pass)
  }

  test("Mann-Whitney Classifier Directional Test: High Metric"){

    val experimentData = Array(
      20.878412601720026, 21.78550581191622, 19.70828084717019, 15.859946903461562,
      19.510058772176841, 19.392005525270303, 22.926528314912101, 17.156530276709894,
      18.655117916226171, 23.422607911610392, 24.055817684156644, 22.62689958516501,
      21.979472967817678, 21.659318607218694, 20.067707574072426, 22.119570836305414,
      16.44037132663307, 21.559622826341268, 21.000181443489218, 17.779369411412663,
      17.806006358943584, 18.537200618386109, 20.555550300210797, 19.552928184468378,
      21.771118944087092, 21.809622806176627, 20.961762909905271, 20.977571577503021,
      19.106303205498794, 16.976918966987078
    )
    val controlData = Array(
      11.1453586774086, 9.9618059885597017, 7.068574306369829, 7.3012661810483657,
      9.7944630265656727, 9.6037918603687302, 6.1867793350339388, 12.345599159550174,
      10.371891733859533, 8.6094300785016724, 9.913162673361084, 10.361375299003466,
      14.206800387518818, 10.946153754753301, 10.215876627044048, 11.739760899484603,
      10.907627468626439, 12.823353694464775, 8.1018609771732137, 8.4981616408804932,
      12.113441374697231, 13.272210809932028, 10.114579381663297, 6.9463958767978067,
      6.2105278541727778, 10.160401684903775, 8.3457712066455461, 8.383822063895833,
      7.0020205070083765, 10.755757401808442
    )

    val experimentMetric = Metric("high-metric", experimentData, "canary")
    val controlMetric = Metric("high-metric", controlData, "baseline")

    val classifier = new MannWhitneyClassifier(tolerance = 0.10, confLevel = 0.95)
    val result = classifier.classify(controlMetric, experimentMetric, MetricDirection.Increase)

    assert(result.classification == High)
    assert(!result.critical)
  }

  test("Mann-Whitney Classifier Directional Test: Low Metric"){

    val experimentData = Array(
      9.7767343120109462, 10.587071850477695, 7.8375506388901766, 9.3243326886113884,
      8.3774505409784421, 8.9359264399606282, 8.2707663324728511, 9.7484308126716925,
      11.623746715547156, 9.2753442679633942, 8.1538047749171518, 10.562020975754294,
      13.498135575451835, 11.054419256110332, 6.9538903590055092, 12.521008342195479,
      9.0216358603546443, 4.9234856699531662, 11.251360203607172, 11.598449508040337,
      8.435232204668182, 11.110228516867279, 10.70063380164755, 8.9212773652115249,
      10.086167067942661, 11.42107515483087, 9.3792347813644952, 7.7980320148552593,
      10.62339807275746, 11.909642062063739
    )
    val controlData = Array(
      20.758113589510348, 22.802059629464559, 20.612340363492208, 20.973274654749986,
      23.469237330498622, 21.53133988954805, 21.767353687439805, 18.308050730374301,
      19.062145167827566, 25.879498436001779, 18.982629726336128, 19.387920182269266,
      18.995398251246982, 24.228420230932805, 20.050985172694688, 18.2340451091756,
      17.870515494460435, 23.058103863806618, 18.400259567501134, 16.971352091177167,
      19.467529066052151, 17.90465423342031, 17.553948617825831, 16.104611843907193,
      24.38171081433039, 15.919294339922635, 20.479409626304697, 19.08307201492406,
      17.455788050722866, 18.853826823235142
    )

    val experimentMetric = Metric("low-metric", experimentData, "canary")
    val controlMetric = Metric("low-metric", controlData, "baseline")

    val classifier = new MannWhitneyClassifier(tolerance = 0.10, confLevel = 0.95)
    val result = classifier.classify(controlMetric, experimentMetric, MetricDirection.Decrease)

    assert(result.classification == Low)
  }

  test("Mann-Whitney Classifier Test: High Metric (Large Input)"){

    val experimentData = Array(
      0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.6666666666666572, 0.0, 0.0, 0.0, 0.0,
      0.0, 0.0, 3.7037037037036811, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.5625000000000142,
      0.0, 0.0, 0.0, 0.0, 3.3333333333333428, 0.0, 1.7543859649122737, 0.0,
      1.8518518518518476, 0.0, 0.0, 1.8518518518518476, 0.0, 0.0, 1.5151515151515156,
      0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.7857142857142776,
      1.3333333333333286, 0.0, 3.1250000000000142, 1.9607843137255117,
      1.4492753623188293, 2.4390243902438868, 1.6393442622950687, 0.0, 0.0, 0.0, 0.0,
      0.0, 0.0, 0.0, 1.6129032258064484, 1.9607843137255117, 1.5873015873015817, 0.0,
      0.0, 2.8169014084507182, 0.0, 0.0, 3.0303030303030454, 0.0, 0.0,
      3.3898305084745743, 0.0, 0.0, 1.234567901234584, 0.0, 0.0, 0.0, 0.0,
      1.7241379310344769, 0.0, 0.0, 0.0, 0.0, 1.6949152542372872, 0.0, 0.0, 0.0, 0.0,
      0.0, 0.0, 0.0, 1.470588235294116, 0.0, 0.0, 1.3698630136986196,
      3.225806451612911, 1.7543859649122879, 3.4482758620689538, 0.0,
      3.3333333333333428, 0.0, 0.0, 0.0, 1.818181818181813, 0.0, 0.0,
      1.4285714285714448, 0.0, 0.0, 1.8867924528301927, 2.0000000000000284, 0.0, 0.0,
      0.0, 2.985074626865682, 0.0, 0.0, 1.6666666666666714, 0.0, 0.0, 1.5625,
      1.9230769230769198, 0.0, 1.8518518518518619, 0.0, 2.7027027027027088, 0.0, 0.0,
      0.0, 1.3513513513513544, 0.0, 1.818181818181813, 0.0, 1.6666666666666572, 0.0,
      0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.818181818181813, 0.0, 1.4285714285714164, 0.0,
      0.0, 4.1095890410959015, 0.0, 0.0, 0.0, 1.8518518518518619, 0.0, 0.0,
      1.8518518518518476, 1.3888888888888857, 0.0, 0.0, 0.0, 0.0, 1.8867924528301785,
      1.4285714285714164, 0.0, 0.0, 1.5625, 1.8867924528301927, 0.0, 0.0, 0.0, 0.0,
      2.040816326530603, 1.818181818181813, 0.0, 0.0, 1.6393442622950687, 0.0,
      1.7241379310344769, 1.5151515151515156, 2.564102564102555, 0.0, 0.0, 0.0, 0.0,
      5.5555555555555571, 0.0, 0.0, 4.0816326530612059, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
      0.0, 0.0, 3.5714285714285836, 0.0, 2.040816326530603, 3.1746031746031633, 0.0,
      0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0
    )

    val controlData = Array(
      0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.5625000000000142, 0.0, 0.0, 0.0,
      0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.3888888888888857, 0.0, 0.0, 0.0, 0.0,
      0.0, 0.0, 0.0, 1.5384615384615614, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
      0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.4925373134328339, 0.0, 0.0, 0.0,
      1.6949152542373014, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
      0.0, 0.0, 1.470588235294116, 0.0, 0.0, 0.0, 0.0, 1.7543859649122879, 0.0, 0.0,
      0.0, 0.0, 0.0, 0.0, 1.8518518518518619, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
      0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.6393442622950687, 0.0, 0.0,
      0.0, 0.0, 0.0, 0.0, 1.7241379310344769, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
      0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.5873015873015817,
      0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
      1.4925373134328339, 0.0, 0.0, 0.0, 3.1249999999999858, 0.0, 0.0, 0.0, 0.0,
      0.0, 0.0, 0.0, 0.0, 0.0, 1.4925373134328339, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
      0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
      2.9850746268656536, 0.0, 2.5000000000000284, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
      0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
      0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
      1.7857142857142776, 0.0, 0.0, 0.0
    )

    val experimentMetric = Metric("pass-metric", experimentData, "canary")
    val controlMetric = Metric("pass-metric", controlData, "baseline")

    val classifier = new MannWhitneyClassifier(tolerance = 0.10, confLevel = 0.95)
    val result = classifier.classify(controlMetric, experimentMetric, MetricDirection.Either)

    assert(result.classification == High)
    assert(!result.critical)
  }

  test("Mann-Whitney Classifier Test - Mean Ratio Effect Size: High"){
    val experimentData = Array(20.0, 22.0, 21.0, 25.0, 30.0)
    val controlData = Array(1.0, 2.2, 1.1, 2.5, 3.0)

    val experimentMetric = Metric("high-metric", experimentData, "canary")
    val controlMetric = Metric("high-metric", controlData, "baseline")

    val classifier = new MannWhitneyClassifier(tolerance = 0.10, confLevel = 0.95, effectSizeThresholds = (0.8, 1.2))
    val result = classifier.classify(controlMetric, experimentMetric, MetricDirection.Either)

    assert(result.classification == High)
  }

  test("Mann-Whitney Classifier Test - Mean Ratio Effect Size: Low"){
    val experimentData = Array(
      9.7767343120109462, 10.587071850477695, 7.8375506388901766, 9.3243326886113884,
      8.3774505409784421, 8.9359264399606282, 8.2707663324728511, 9.7484308126716925,
      11.623746715547156, 9.2753442679633942, 8.1538047749171518, 10.562020975754294,
      13.498135575451835, 11.054419256110332, 6.9538903590055092, 12.521008342195479,
      9.0216358603546443, 4.9234856699531662, 11.251360203607172, 11.598449508040337,
      8.435232204668182, 11.110228516867279, 10.70063380164755, 8.9212773652115249,
      10.086167067942661, 11.42107515483087, 9.3792347813644952, 7.7980320148552593,
      10.62339807275746, 11.909642062063739
    )
    val controlData = Array(
      20.758113589510348, 22.802059629464559, 20.612340363492208, 20.973274654749986,
      23.469237330498622, 21.53133988954805, 21.767353687439805, 18.308050730374301,
      19.062145167827566, 25.879498436001779, 18.982629726336128, 19.387920182269266,
      18.995398251246982, 24.228420230932805, 20.050985172694688, 18.2340451091756,
      17.870515494460435, 23.058103863806618, 18.400259567501134, 16.971352091177167,
      19.467529066052151, 17.90465423342031, 17.553948617825831, 16.104611843907193,
      24.38171081433039, 15.919294339922635, 20.479409626304697, 19.08307201492406,
      17.455788050722866, 18.853826823235142
    )

    val experimentMetric = Metric("low-metric", experimentData, "canary")
    val controlMetric = Metric("low-metric", controlData, "baseline")

    val classifier = new MannWhitneyClassifier(tolerance = 0.10, confLevel = 0.95, effectSizeThresholds = (0.8, 1.2))
    val result = classifier.classify(controlMetric, experimentMetric, MetricDirection.Decrease)

    assert(result.classification == Low)
  }

  test("Mann-Whitney Classifier Test - Mean Ratio Effect Size: Pass"){
    val experimentData = Array(10.0, 12.2, 10.1, 12.5, 23.0)
    val controlData = Array(20.0, 22.0, 21.0, 25.0, 30.0)

    val experimentMetric = Metric("pass-metric", experimentData, "canary")
    val controlMetric = Metric("pass-metric", controlData, "baseline")

    val classifier = new MannWhitneyClassifier(tolerance = 0.10, confLevel = 0.95, effectSizeThresholds = (0.5, 1.0))
    val result = classifier.classify(controlMetric, experimentMetric, MetricDirection.Either, NaNStrategy.Remove)

    assert(result.classification == Pass)
  }

  test("Mann-Whitney Classifier Test - Mean Ratio Effect Size: Pass (Large Input)"){
    val experimentData = Array(
      0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.6666666666666572, 0.0, 0.0, 0.0, 0.0,
      0.0, 0.0, 3.7037037037036811, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.5625000000000142,
      0.0, 0.0, 0.0, 0.0, 3.3333333333333428, 0.0, 1.7543859649122737, 0.0,
      1.8518518518518476, 0.0, 0.0, 1.8518518518518476, 0.0, 0.0, 1.5151515151515156,
      0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.7857142857142776,
      1.3333333333333286, 0.0, 3.1250000000000142, 1.9607843137255117,
      1.4492753623188293, 2.4390243902438868, 1.6393442622950687, 0.0, 0.0, 0.0, 0.0,
      0.0, 0.0, 0.0, 1.6129032258064484, 1.9607843137255117, 1.5873015873015817, 0.0,
      0.0, 2.8169014084507182, 0.0, 0.0, 3.0303030303030454, 0.0, 0.0,
      3.3898305084745743, 0.0, 0.0, 1.234567901234584, 0.0, 0.0, 0.0, 0.0,
      1.7241379310344769, 0.0, 0.0, 0.0, 0.0, 1.6949152542372872, 0.0, 0.0, 0.0, 0.0,
      0.0, 0.0, 0.0, 1.470588235294116, 0.0, 0.0, 1.3698630136986196,
      3.225806451612911, 1.7543859649122879, 3.4482758620689538, 0.0,
      3.3333333333333428, 0.0, 0.0, 0.0, 1.818181818181813, 0.0, 0.0,
      1.4285714285714448, 0.0, 0.0, 1.8867924528301927, 2.0000000000000284, 0.0, 0.0,
      0.0, 2.985074626865682, 0.0, 0.0, 1.6666666666666714, 0.0, 0.0, 1.5625,
      1.9230769230769198, 0.0, 1.8518518518518619, 0.0, 2.7027027027027088, 0.0, 0.0,
      0.0, 1.3513513513513544, 0.0, 1.818181818181813, 0.0, 1.6666666666666572, 0.0,
      0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.818181818181813, 0.0, 1.4285714285714164, 0.0,
      0.0, 4.1095890410959015, 0.0, 0.0, 0.0, 1.8518518518518619, 0.0, 0.0,
      1.8518518518518476, 1.3888888888888857, 0.0, 0.0, 0.0, 0.0, 1.8867924528301785,
      1.4285714285714164, 0.0, 0.0, 1.5625, 1.8867924528301927, 0.0, 0.0, 0.0, 0.0,
      2.040816326530603, 1.818181818181813, 0.0, 0.0, 1.6393442622950687, 0.0,
      1.7241379310344769, 1.5151515151515156, 2.564102564102555, 0.0, 0.0, 0.0, 0.0,
      5.5555555555555571, 0.0, 0.0, 4.0816326530612059, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
      0.0, 0.0, 3.5714285714285836, 0.0, 2.040816326530603, 3.1746031746031633, 0.0,
      0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0
    )

    val controlData = Array(
      0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.5625000000000142, 0.0, 0.0, 0.0,
      0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.3888888888888857, 0.0, 0.0, 0.0, 0.0,
      0.0, 0.0, 0.0, 1.5384615384615614, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
      0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.4925373134328339, 0.0, 0.0, 0.0,
      1.6949152542373014, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
      0.0, 0.0, 1.470588235294116, 0.0, 0.0, 0.0, 0.0, 1.7543859649122879, 0.0, 0.0,
      0.0, 0.0, 0.0, 0.0, 1.8518518518518619, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
      0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.6393442622950687, 0.0, 0.0,
      0.0, 0.0, 0.0, 0.0, 1.7241379310344769, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
      0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.5873015873015817,
      0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
      1.4925373134328339, 0.0, 0.0, 0.0, 3.1249999999999858, 0.0, 0.0, 0.0, 0.0,
      0.0, 0.0, 0.0, 0.0, 0.0, 1.4925373134328339, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
      0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
      2.9850746268656536, 0.0, 2.5000000000000284, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
      0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
      0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
      1.7857142857142776, 0.0, 0.0, 0.0
    )

    val experimentMetric = Metric("pass-metric", experimentData, "canary")
    val controlMetric = Metric("pass-metric", controlData, "baseline")

    val classifier = new MannWhitneyClassifier(tolerance = 0.10, confLevel = 0.95, effectSizeThresholds = (1.0, 5.0))
    val result = classifier.classify(controlMetric, experimentMetric, MetricDirection.Either)

    assert(result.classification == Pass)
  }

  test("Mann-Whitney Classifier Test - CLES Effect Size: Pass"){
    val experimentData = Array(
      0.00000000e+00, 0.00000000e+00, 1.46580998e-04, 0.00000000e+00,
      0.00000000e+00, 0.00000000e+00, 7.94701987e-05, 0.00000000e+00,
      0.00000000e+00, 0.00000000e+00, 2.68889486e-05, 0.00000000e+00,
      0.00000000e+00, 2.64935753e-05, 0.00000000e+00, 0.00000000e+00,
      0.00000000e+00, 2.61608895e-05, 1.27824931e-04, 0.00000000e+00,
      0.00000000e+00, 0.00000000e+00, 2.54511211e-05, 2.58665287e-05,
      7.63339355e-05, 0.00000000e+00, 2.54239443e-05, 0.00000000e+00,
      0.00000000e+00, 0.00000000e+00, 2.61001201e-05, 5.20101940e-05,
      0.00000000e+00, 2.64452319e-05, 5.27551370e-05, 7.83719533e-05,
      5.19467027e-05, 2.58344528e-05, 2.58899676e-05, 0.00000000e+00,
      2.59497613e-05, 2.57938043e-05, 7.78553448e-05, 2.60593110e-05,
      2.61001201e-05, 0.00000000e+00, 0.00000000e+00, 2.59349551e-05,
      2.60559160e-05, 0.00000000e+00, 0.00000000e+00, 0.00000000e+00,
      0.00000000e+00, 0.00000000e+00, 0.00000000e+00, 2.58819267e-05,
      0.00000000e+00, 2.58825965e-05, 2.57572635e-05, 2.56877906e-05
    )

    val controlData = Array(
      0.00000000e+00, 0.00000000e+00, 0.00000000e+00, 0.00000000e+00,
      0.00000000e+00, 0.00000000e+00, 0.00000000e+00, 0.00000000e+00,
      2.91528191e-05, 0.00000000e+00, 0.00000000e+00, 0.00000000e+00,
      2.98284862e-05, 5.99916012e-05, 0.00000000e+00, 5.92276712e-05,
      0.00000000e+00, 2.98053709e-05, 0.00000000e+00, 2.97309350e-05,
      5.92153960e-05, 0.00000000e+00, 0.00000000e+00, 0.00000000e+00,
      2.92800047e-05, 0.00000000e+00, 2.94550810e-05, 0.00000000e+00,
      0.00000000e+00, 0.00000000e+00, 5.83958656e-05, 0.00000000e+00,
      0.00000000e+00, 0.00000000e+00, 0.00000000e+00, 0.00000000e+00,
      0.00000000e+00, 0.00000000e+00, 0.00000000e+00, 0.00000000e+00,
      2.88600289e-05, 2.90334756e-05, 2.91740817e-05, 0.00000000e+00,
      0.00000000e+00, 0.00000000e+00, 0.00000000e+00, 0.00000000e+00,
      0.00000000e+00, 2.92765758e-05, 0.00000000e+00, 0.00000000e+00,
      0.00000000e+00, 0.00000000e+00, 0.00000000e+00, 2.94637596e-05,
      5.89136326e-05, 0.00000000e+00, 0.00000000e+00, 0.00000000e+00
    )

    val experimentMetric = Metric("pass-metric", experimentData, "canary")
    val controlMetric = Metric("pass-metric", controlData, "baseline")

    val classifier = new MannWhitneyClassifier(tolerance = 0.20, confLevel = 0.98,
      effectSizeThresholds = (0.5, 0.8), effectSizeMeasure="cles")
    val result = classifier.classify(controlMetric, experimentMetric, MetricDirection.Either)

    assert(result.classification == Pass)
  }

  test("Mann-Whitney Classifier Test - CLES Effect Size: High"){
    val experimentData = Array(
      0.00689808, 0.00568664, 0.00680876, 0.00642045, 0.00561825,
      0.00472294, 0.00398378, 0.00392403, 0.00421201, 0.0047529 ,
      0.00460853, 0.00439888, 0.00561875, 0.01998866, 0.0124369 ,
      0.00624391, 0.00444361, 0.00442059, 0.00372095, 0.00540736,
      0.00715402, 0.00425974, 0.00414409, 0.01172436, 0.0125959 ,
      0.00371891, 0.00429855, 0.00369876, 0.00325709, 0.00415943,
      0.00371754, 0.00445212, 0.00521721, 0.00443938, 0.00380859,
      0.01602997, 0.00788885, 0.0038155 , 0.00326921, 0.00336928,
      0.00365917, 0.00333869, 0.00427639, 0.00355911, 0.00381224,
      0.00360057, 0.00349305, 0.00401164, 0.00351947, 0.00356299,
      0.003407  , 0.00414397, 0.00393346, 0.00413318, 0.00359041,
      0.00366314, 0.00309383, 0.00383733, 0.00363324, 0.00280674
    )

    val controlData = Array(
      0.00081956, 0.00239282, 0.00133685, 0.00094917, 0.00029518,
      0.00035344, 0.00048194, 0.00034893, 0.0003202 , 0.00079814,
      0.00090881, 0.00072599, 0.00037819, 0.00038286, 0.00057604,
      0.00064821, 0.00032194, 0.0002245 , 0.00025202, 0.00071997,
      0.00022721, 0.00042578, 0.00042496, 0.00045613, 0.00037072,
      0.0003317 , 0.00032889, 0.00032749, 0.00036093, 0.00029285,
      0.00038401, 0.00044467, 0.00071086, 0.00036692, 0.00052079,
      0.00074974, 0.00128791, 0.00062046, 0.00086345, 0.00118563,
      0.00103379, 0.00028913, 0.00057628, 0.00057943, 0.00048735,
      0.00029409, 0.0002005 , 0.00016612, 0.00089312, 0.00059367,
      0.00061706, 0.00163313, 0.00097784, 0.00029305, 0.00058839,
      0.00085212, 0.00048298, 0.00035546, 0.00025866, 0.00038767
    )

    val experimentMetric = Metric("high-metric", experimentData, "canary")
    val controlMetric = Metric("high-metric", controlData, "baseline")

    val classifier = new MannWhitneyClassifier(tolerance = 0.20, confLevel = 0.98,
      effectSizeThresholds = (0.2, 0.8), effectSizeMeasure="cles")
    val result = classifier.classify(controlMetric, experimentMetric, MetricDirection.Either)

    assert(result.classification == High)
  }

  test("Mann-Whitney Classifier Test - CLES Effect Size: Low"){
    val experimentData = Array(
      24.24693907, 26.13330099, 22.50056744, 24.36646841, 23.19935673,
      19.43801304, 20.99654102, 18.90793651, 21.31949881, 21.83528381,
      21.64211206, 23.13365968, 21.09163134, 19.45540615, 20.74783158,
      22.25527831, 19.82051791, 20.9865487 , 19.94450026, 23.2904056 ,
      18.18408424, 22.13843542, 19.42896044, 19.84140464, 23.91557662,
      21.00981557, 21.8594633 , 23.11630163, 22.95700392, 17.80045135
    )

    val controlData = Array(
      25.09626848, 26.15032198, 24.88431438, 24.18870163, 22.14684987,
      28.01743443, 21.29910501, 27.24688773, 28.64187535, 27.03504445,
      24.67888311, 22.35137878, 27.41640397, 26.00225548, 23.02118534,
      25.99647292, 22.77446912, 25.56704719, 25.57025827, 21.2721602 ,
      23.12987357, 24.59111604, 27.48865016, 27.21967755, 23.5951111 ,
      22.5429607 , 25.83015564, 23.31965305, 24.3911644 , 25.11468194
    )

    val experimentMetric = Metric("low-metric", experimentData, "canary")
    val controlMetric = Metric("low-metric", controlData, "baseline")

    val classifier = new MannWhitneyClassifier(tolerance = 0.20, confLevel = 0.98,
      effectSizeThresholds = (0.2, 0.8), effectSizeMeasure="cles")
    val result = classifier.classify(controlMetric, experimentMetric, MetricDirection.Either)

    assert(result.classification == Low)
  }

  test("Mann-Whitney Classifier Test - Critical Effect Size: Critical High"){
    val experimentData = Array(20.0, 22.0, 21.0, 25.0, 30.0)
    val controlData = Array(1.0, 2.2, 1.1, 2.5, 3.0)

    val experimentMetric = Metric("high-critical-metric", experimentData, "canary")
    val controlMetric = Metric("high-critical-metric", controlData, "baseline")

    val classifier = new MannWhitneyClassifier(tolerance = 0.10, confLevel = 0.95, criticalThresholds = (1.0, 1.2))
    val result = classifier.classify(controlMetric, experimentMetric, MetricDirection.Either, isCriticalMetric = true)

    assert(result.classification == High)
    assert(result.critical)
  }

  test("Mann-Whitney Classifier Test - Critical Effect Size: Critical Low"){
    val experimentData = Array(2.0, 2.0, 2.0, 2.0, 3.0)
    val controlData = Array(10.0, 20.2, 10.1, 20.5, 30.0)

    val experimentMetric = Metric("low-critical-metric", experimentData, "canary")
    val controlMetric = Metric("low-critical-metric", controlData, "baseline")

    val classifier = new MannWhitneyClassifier(tolerance = 0.10, confLevel = 0.95, criticalThresholds = (1.2, 1.0))
    val result = classifier.classify(controlMetric, experimentMetric, MetricDirection.Either, isCriticalMetric = true)

    assert(result.classification == Low)
    assert(result.critical)
  }

  test("Mann-Whitney Classifier Test - Critical Effect Size: High"){
    val experimentData = Array(20.0, 22.0, 21.0, 25.0, 30.0)
    val controlData = Array(1.0, 2.2, 1.1, 2.5, 3.0)

    val experimentMetric = Metric("high-metric", experimentData, "canary")
    val controlMetric = Metric("high-metric", controlData, "baseline")

    val classifier = new MannWhitneyClassifier(
      tolerance = 0.10,
      confLevel = 0.95,
      effectSizeThresholds = (1.0, 5.0),
      criticalThresholds = (1.0, 13.0))
    val result = classifier.classify(controlMetric, experimentMetric, MetricDirection.Either, isCriticalMetric = true)

    assert(result.classification == High)
    assert(!result.critical)
  }

  test("Mann-Whitney Classifier Test - Critical Effect Size: Pass"){
    val experimentData = Array(20.0, 22.0, 21.0, 25.0, 30.0)
    val controlData = Array(1.0, 2.2, 1.1, 2.5, 3.0)

    val experimentMetric = Metric("pass-metric", experimentData, "canary")
    val controlMetric = Metric("pass-metric", controlData, "baseline")

    val classifier = new MannWhitneyClassifier(
      tolerance = 0.10,
      confLevel = 0.95,
      effectSizeThresholds = (1.0, 13.0),
      criticalThresholds = (1.0, 15.0))
    val result = classifier.classify(controlMetric, experimentMetric, MetricDirection.Either, isCriticalMetric = true)

    assert(result.classification == Pass)
    assert(!result.critical)
  }

  test("Mann-Whitney Classifier Test - Critical Effect Size: Critical High (Default Thresholds)"){
    val experimentData = Array(20.0, 22.0, 21.0, 25.0, 30.0)
    val controlData = Array(1.0, 2.2, 1.1, 2.5, 3.0)

    val experimentMetric = Metric("high-metric", experimentData, "canary")
    val controlMetric = Metric("high-metric", controlData, "baseline")

    val classifier = new MannWhitneyClassifier(tolerance = 0.10, confLevel = 0.95)
    val result = classifier.classify(controlMetric, experimentMetric, MetricDirection.Either, isCriticalMetric = true)

    assert(result.classification == High)
    assert(result.critical)
  }

  test("Mann-Whitney Tied Observations") {
    val experimentData = Array(1.0, 1.0, 1.0)
    val controlData = Array(1.0, 1.0)

    val experimentMetric = Metric("test-metric", experimentData, "canary")
    val controlMetric = Metric("test-metric", controlData, "baseline")

    val classifier = new MannWhitneyClassifier(tolerance = 0.10, confLevel = 0.95)
    val result = classifier.classify(controlMetric, experimentMetric, MetricDirection.Decrease)

    assert(result.classification == Pass)
  }

  test("Mann-Whitney Identical Populations"){
    val experimentData = Array(1.0, 2.0, 3.0, 4.0, 5.0)
    val controlData = Array(5.0, 4.0, 3.0, 2.0, 1.0)

    val experimentMetric = Metric("test-metric", experimentData, "canary")
    val controlMetric = Metric("test-metric", controlData, "baseline")

    val classifier = new MannWhitneyClassifier(tolerance = 0.10, confLevel = 0.95)
    val result = classifier.classify(controlMetric, experimentMetric, MetricDirection.Decrease)

    assert(result.classification == Pass)
  }

  test("Mann-Whitney Leading Zero Identical Population") {
    val experimentData = Array(0.0, 1.0, 1.0, 1.0, 1.0)
    val controlData = Array(1.0, 1.0, 1.0, 1.0, 1.0)

    val experimentMetric = Metric("test-metric", experimentData, "canary")
    val controlMetric = Metric("test-metric", controlData, "baseline")

    val classifier = new MannWhitneyClassifier(tolerance = 0.10, confLevel = 0.95)
    val result = classifier.classify(controlMetric, experimentMetric, MetricDirection.Decrease)

    assert(result.classification == Pass)
  }

  test("Mann-Whitney Missing Experiment Data") {
    val experimentData = Array[Double]()
    val controlData = Array(1.0, 2.0, 3.0, 4.0, 5.0)

    val experimentMetric = Metric("test-metric", experimentData, "canary")
    val controlMetric = Metric("test-metric", controlData, "baseline")

    val classifier = new MannWhitneyClassifier(tolerance = 0.10, confLevel = 0.95)
    val result = classifier.classify(controlMetric, experimentMetric, MetricDirection.Decrease)

    assert(result.classification == Nodata)
  }

  test("Mann-Whitney Missing Experiment Data: Critical Metric") {
    val experimentData = Array[Double]()
    val controlData = Array(1.0, 2.0, 3.0, 4.0, 5.0)

    val experimentMetric = Metric("test-critical-metric", experimentData, "canary")
    val controlMetric = Metric("test-critical-metric", controlData, "baseline")

    val classifier = new MannWhitneyClassifier(tolerance = 0.10, confLevel = 0.95)
    val result = classifier.classify(controlMetric, experimentMetric, MetricDirection.Decrease, isCriticalMetric = true)

    assert(result.classification == Nodata)
    assert(result.critical)
  }

  test("Mann-Whitney Missing Experiment Data with NaN Replacement") {
    val experimentData = Array[Double]()
    val controlData = Array(1.0, 2.0, 3.0, 4.0, 5.0)

    val experimentMetric = Metric("test-metric", experimentData, "canary")
    val controlMetric = Metric("test-metric", controlData, "baseline")

    val classifier = new MannWhitneyClassifier(tolerance = 0.10, confLevel = 0.95)
    val result = classifier.classify(controlMetric, experimentMetric, MetricDirection.Decrease, NaNStrategy.Replace)

    assert(result.classification == Pass)
  }

  test("Mann-Whitney Missing Data: Reason String") {
    val experimentData = Array[Double]()
    val controlData = Array[Double]()

    val experimentMetric = Metric("test-critical-metric", experimentData, "canary")
    val controlMetric = Metric("test-critical-metric", controlData, "baseline")

    val classifier = new MannWhitneyClassifier(tolerance = 0.10, confLevel = 0.95)
    val result = classifier.classify(controlMetric, experimentMetric, MetricDirection.Decrease, isCriticalMetric = true)

    result.reason should not be empty
  }

  test("Mann-Whitney Majority Tied Observations"){
    val experimentData = Array(1.0, 1.0, 1.0, 1.0, 10.0)
    val controlData = Array(1.0, 1.0, 1.0, 1.0, 1.0)

    val experimentMetric = Metric("pass-metric", experimentData, "canary")
    val controlMetric = Metric("pass-metric", controlData, "baseline")

    val classifier = new MannWhitneyClassifier(tolerance = 0.10, confLevel = 0.95)
    val result = classifier.classify(controlMetric, experimentMetric, MetricDirection.Either)

    assert(result.classification == Pass)
  }

  test("Mann-Whitney Tied Ranks"){
    val experimentData = Array(10.0, 10.0, 10.0, 10.0, 10.0)
    val controlData = Array(1.0, 1.0, 1.0, 1.0, 1.0)

    val experimentMetric = Metric("high-metric", experimentData, "canary")
    val controlMetric = Metric("high-metric", controlData, "baseline")

    val classifier = new MannWhitneyClassifier(tolerance = 0.10, confLevel = 0.95)
    val result = classifier.classify(controlMetric, experimentMetric, MetricDirection.Either)

    assert(result.classification == High)
  }

  test("Mann-Whitney Tied Ranks With Zeros"){
    val experimentData = Array(10.0, 10.0, 10.0, 10.0, 10.0)
    val controlData = Array(0.0, 0.0, 0.0, 0.0, 0.0)

    val experimentMetric = Metric("high-metric", experimentData, "canary")
    val controlMetric = Metric("high-metric", controlData, "baseline")

    val classifier = new MannWhitneyClassifier(tolerance = 0.10, confLevel = 0.95)
    val result = classifier.classify(controlMetric, experimentMetric, MetricDirection.Either)

    assert(result.classification == High)
  }

  test("Mann-Whitney Zero Mean: High Metric with Effect Size"){
    val experimentData =  Array(54.9, 54.5, 55.1, 55.6, 57.4)
    val controlData = Array(0.0, 0.0, 0.0, 0.0, 0.0)

    val experimentMetric = Metric("high-metric", experimentData, "canary")
    val controlMetric = Metric("high-metric", controlData, "baseline")

    val classifier = new MannWhitneyClassifier(tolerance = 0.10, confLevel = 0.95, effectSizeThresholds = (0.8, 1.2))
    val result = classifier.classify(controlMetric, experimentMetric, MetricDirection.Either)

    assert(result.classification == High)
    assert(result.deviation.isNaN)
  }

  test("Mann-Whitney Zero Mean: Low Metric with Effect Size"){
    val experimentData =  Array(0.0, 0.0, 0.0, 0.0, 0.0)
    val controlData = Array(54.9, 54.5, 55.1, 55.6, 57.4)

    val experimentMetric = Metric("low-metric", experimentData, "canary")
    val controlMetric = Metric("low-metric", controlData, "baseline")

    val classifier = new MannWhitneyClassifier(tolerance = 0.10, confLevel = 0.95, effectSizeThresholds = (0.8, 1.2))
    val result = classifier.classify(controlMetric, experimentMetric, MetricDirection.Either)

    assert(result.classification == Low)
    assert(result.deviation.isNaN)
  }

  test("Mann-Whitney Zero Mean: Critical Metric"){
    val experimentData = Array(
      0.05000000074505806, 0.05000000074505806, 0.01666666753590107,
      0.01666666753590107, 0.01666666753590107, 0.05000000074505806,
      0.03333333507180214, 0.05000000260770321, 0.05000000260770321,
      0.13333333656191826,
    )
    val controlData = Array(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)

    val experimentMetric = Metric("high-metric", experimentData, "canary")
    val controlMetric = Metric("high-metric", controlData, "baseline")

    val classifier = new MannWhitneyClassifier(tolerance = 0.10, confLevel = 0.95, criticalThresholds=(0.8, 1.2))
    val result = classifier.classify(controlMetric, experimentMetric, MetricDirection.Either, NaNStrategy.Replace, isCriticalMetric = true)

    assert(result.classification == High)
    assert(result.critical)
  }

  test("Mann-Whitney Zero Mean: Pass Metric"){
    val experimentData =  Array(0.0, 0.0, 0.0, 0.0, 0.0)
    val controlData = Array(0.0, 0.0, 0.0, 0.0, 0.0)

    val experimentMetric = Metric("pass-metric", experimentData, "canary")
    val controlMetric = Metric("pass-metric", controlData, "baseline")

    val classifier = new MannWhitneyClassifier(tolerance = 0.10, confLevel = 0.95, effectSizeThresholds = (0.8, 1.2))
    val result = classifier.classify(controlMetric, experimentMetric, MetricDirection.Either)

    assert(result.classification == Pass)
    assert(result.deviation == 1.0)
  }

  test("Mean Inequality Classifier Test: High Metric"){
    val experimentData = Array(10.0, 20.0, 30.0, 40.0, 50.0)
    val controlData = Array(1.0, 2.0, 3.0, 4.0, 5.0)

    val experimentMetric = Metric("high-metric", experimentData, "canary")
    val controlMetric = Metric("high-metric", controlData, "baseline")

    val classifier = new MeanInequalityClassifier()
    val result = classifier.classify(controlMetric, experimentMetric, MetricDirection.Either)

    assert(result.classification == High)
  }

  test("Mean Inequality Classifier Test: Low Metric"){
    val experimentData = Array(1.0, 2.0, 3.0, 4.0, 5.0)
    val controlData = Array(10.0, 20.0, 30.0, 40.0, 50.0)

    val experimentMetric = Metric("low-metric", experimentData, "canary")
    val controlMetric = Metric("low-metric", controlData, "baseline")

    val classifier = new MeanInequalityClassifier()
    val result = classifier.classify(controlMetric, experimentMetric, MetricDirection.Either)

    assert(result.classification == Low)
  }

  test("Mean Inequality Classifier Test: Pass Metric"){
    val experimentData = Array(1.0, 2.0, 3.0, 4.0, 5.0)
    val controlData = Array(1.0, 2.0, 3.0, 4.0, 5.0)

    val experimentMetric = Metric("pass-metric", experimentData, "canary")
    val controlMetric = Metric("pass-metric", controlData, "baseline")

    val classifier = new MeanInequalityClassifier()
    val result = classifier.classify(controlMetric, experimentMetric, MetricDirection.Either)

    assert(result.classification == Pass)
  }
}
