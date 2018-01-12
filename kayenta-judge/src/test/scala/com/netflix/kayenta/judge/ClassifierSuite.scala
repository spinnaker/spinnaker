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
import com.netflix.kayenta.r.MannWhitney
import org.scalatest.FunSuite


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

    val mw = new MannWhitney()
    val classifier = new MannWhitneyClassifier(fraction = 0.10, confLevel = 0.95, mw)
    val result = classifier.classify(controlMetric, experimentMetric, MetricDirection.Either)
    mw.disconnect()

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

    val mw = new MannWhitney()
    val classifier = new MannWhitneyClassifier(fraction = 0.10, confLevel = 0.95, mw)
    val result = classifier.classify(controlMetric, experimentMetric, MetricDirection.Either)
    mw.disconnect()

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

    val mw = new MannWhitney()
    val classifier = new MannWhitneyClassifier(fraction = 0.10, confLevel = 0.95, mw)
    val result = classifier.classify(controlMetric, experimentMetric, MetricDirection.Either)
    mw.disconnect()

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

    val mw = new MannWhitney()
    val classifier = new MannWhitneyClassifier(fraction = 0.10, confLevel = 0.95, mw)
    val result = classifier.classify(controlMetric, experimentMetric, MetricDirection.Increase)
    mw.disconnect()

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

    val mw = new MannWhitney()
    val classifier = new MannWhitneyClassifier(fraction = 0.10, confLevel = 0.95, mw)
    val result = classifier.classify(controlMetric, experimentMetric, MetricDirection.Decrease)
    mw.disconnect()

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

    val mw = new MannWhitney()
    val classifier = new MannWhitneyClassifier(fraction = 0.10, confLevel = 0.95, mw)
    val result = classifier.classify(controlMetric, experimentMetric, MetricDirection.Increase)
    mw.disconnect()

    assert(result.classification == High)
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

    val mw = new MannWhitney()
    val classifier = new MannWhitneyClassifier(fraction = 0.10, confLevel = 0.95, mw)
    val result = classifier.classify(controlMetric, experimentMetric, MetricDirection.Decrease)
    mw.disconnect()

    assert(result.classification == Low)
  }

  test("Mann-Whitney Tied Observations") {
    val experimentData = Array(1.0, 1.0, 1.0)
    val controlData = Array(1.0, 1.0)

    val experimentMetric = Metric("test-metric", experimentData, "canary")
    val controlMetric = Metric("test-metric", controlData, "baseline")

    val mw = new MannWhitney()
    val classifier = new MannWhitneyClassifier(fraction = 0.10, confLevel = 0.95, mw)
    val result = classifier.classify(controlMetric, experimentMetric, MetricDirection.Decrease)
    mw.disconnect()

    assert(result.classification == Pass)
  }

  test("Mann-Whitney Identical Populations"){
    val experimentData = Array(1.0, 2.0, 3.0, 4.0, 5.0)
    val controlData = Array(5.0, 4.0, 3.0, 2.0, 1.0)

    val experimentMetric = Metric("test-metric", experimentData, "canary")
    val controlMetric = Metric("test-metric", controlData, "baseline")

    val mw = new MannWhitney()
    val classifier = new MannWhitneyClassifier(fraction = 0.10, confLevel = 0.95, mw)
    val result = classifier.classify(controlMetric, experimentMetric, MetricDirection.Decrease)
    mw.disconnect()

    assert(result.classification == Pass)
  }

  test("Mann-Whitney Leading Zero Identical Population") {
    val experimentData = Array(0.0, 1.0, 1.0, 1.0, 1.0)
    val controlData = Array(1.0, 1.0, 1.0, 1.0, 1.0)

    val experimentMetric = Metric("test-metric", experimentData, "canary")
    val controlMetric = Metric("test-metric", controlData, "baseline")

    val mw = new MannWhitney()
    val classifier = new MannWhitneyClassifier(fraction = 0.10, confLevel = 0.95, mw)
    val result = classifier.classify(controlMetric, experimentMetric, MetricDirection.Decrease)
    mw.disconnect()

    assert(result.classification == Pass)
  }

  test("Mann-Whitney Missing Experiment Data") {
    val experimentData = Array[Double]()
    val controlData = Array(1.0, 2.0, 3.0, 4.0, 5.0)

    val experimentMetric = Metric("test-metric", experimentData, "canary")
    val controlMetric = Metric("test-metric", controlData, "baseline")

    val mw = new MannWhitney()
    val classifier = new MannWhitneyClassifier(fraction = 0.10, confLevel = 0.95, mw)
    val result = classifier.classify(controlMetric, experimentMetric, MetricDirection.Decrease)
    mw.disconnect()

    assert(result.classification == Nodata)
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
