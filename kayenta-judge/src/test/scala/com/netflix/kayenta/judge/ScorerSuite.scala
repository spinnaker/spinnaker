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

import com.netflix.kayenta.canary.results.CanaryAnalysisResult
import com.netflix.kayenta.judge.scorers.WeightedSumScorer
import com.netflix.kayenta.judge.classifiers.metric.{Pass, High, Low, Nodata}
import org.scalatest.FunSuite
import org.scalatest.Matchers._

import scala.collection.JavaConverters._

class ScorerSuite extends FunSuite{

  test("Weighted Sum Group Scorer: Single Metric Pass"){
    val groupWeights = Map[String, Double]()
    val weightedSumScorer = new WeightedSumScorer(groupWeights)

    val passMetric = CanaryAnalysisResult.builder()
      .name("test-metric-pass")
      .classification(Pass.toString)
      .groups(List[String]("test-group").asJava)
      .build()

    val scores = weightedSumScorer.score(List(passMetric))
    assert(scores.summaryScore == 100.0)
  }

  test("Weighted Sum Group Scorer: Single Metric Fail"){
    val groupWeights = Map[String, Double]()
    val weightedSumScorer = new WeightedSumScorer(groupWeights)

    val passMetric = CanaryAnalysisResult.builder()
      .name("test-metric-high")
      .classification(High.toString)
      .groups(List[String]("test-group").asJava)
      .build()

    val scores = weightedSumScorer.score(List(passMetric))
    assert(scores.summaryScore == 0.0)
  }

  test("Weighted Sum Group Scorer: One Group, Two Metrics"){
    val groupWeights = Map[String, Double]()
    val weightedSumScorer = new WeightedSumScorer(groupWeights)

    val passMetric = CanaryAnalysisResult.builder()
      .name("test-metric-pass")
      .classification(Pass.toString)
      .groups(List[String]("test-group").asJava)
      .build()

    val highMetric = CanaryAnalysisResult.builder()
      .name("test-metric-high")
      .classification(High.toString)
      .groups(List[String]("test-group").asJava)
      .build()

    val scores = weightedSumScorer.score(List(passMetric, highMetric))
    assert(scores.summaryScore == 50.0)
  }

  test("Weighted Sum Group Scorer: One Group, Three Metrics"){
    val groupWeights = Map[String, Double]()
    val weightedSumScorer = new WeightedSumScorer(groupWeights)

    val passMetric = CanaryAnalysisResult.builder()
      .name("test-metric-pass")
      .classification(Pass.toString)
      .groups(List[String]("test-group").asJava)
      .build()

    val highMetric = CanaryAnalysisResult.builder()
      .name("test-metric-high")
      .classification(High.toString)
      .groups(List[String]("test-group").asJava)
      .build()

    val lowMetric = CanaryAnalysisResult.builder()
      .name("test-metric-low")
      .classification(Low.toString)
      .groups(List[String]("test-group").asJava)
      .build()

    val scores = weightedSumScorer.score(List(passMetric, highMetric, lowMetric))
    assert(scores.summaryScore === (33.33 +- 1.0e-2))
  }

  test("Weighted Sum Group Scorer: Two Groups, Three Metrics"){
    val groupWeights = Map[String, Double]("group-1" -> 75.0, "group-2" -> 25.0)
    val weightedSumScorer = new WeightedSumScorer(groupWeights)

    val passMetric1 = CanaryAnalysisResult.builder()
      .name("test-metric-pass-1")
      .classification(Pass.toString)
      .groups(List[String]("group-1").asJava)
      .build()

    val passMetric2 = CanaryAnalysisResult.builder()
      .name("test-metric-pass-2")
      .classification(Pass.toString)
      .groups(List[String]("group-1").asJava)
      .build()

    val failMetric = CanaryAnalysisResult.builder()
      .name("test-metric-fail")
      .classification(High.toString)
      .groups(List[String]("group-2").asJava)
      .build()

    val scores = weightedSumScorer.score(List(passMetric1, passMetric2, failMetric))
    assert(scores.summaryScore == 75.0)
  }

  test("Weighted Sum Group Scorer: Two Groups, Three Metrics (Equal Weight)"){
    val groupWeights = Map[String, Double]("group-1" -> 50.0, "group-2" -> 50.0)
    val weightedSumScorer = new WeightedSumScorer(groupWeights)

    val passMetric = CanaryAnalysisResult.builder()
      .name("test-metric-pass")
      .classification(Pass.toString)
      .groups(List[String]("group-1").asJava)
      .build()

    val failMetric1 = CanaryAnalysisResult.builder()
      .name("test-metric-fail-1")
      .classification(High.toString)
      .groups(List[String]("group-1").asJava)
      .build()

    val failMetric2 = CanaryAnalysisResult.builder()
      .name("test-metric-fail-2")
      .classification(High.toString)
      .groups(List[String]("group-2").asJava)
      .build()

    val scores = weightedSumScorer.score(List(passMetric, failMetric1, failMetric2))
    assert(scores.summaryScore == 25.0)
  }

  test("Weighted Sum Group Scorer: No Data"){
    val groupWeights = Map[String, Double]()
    val weightedSumScorer = new WeightedSumScorer(groupWeights)

    val metricClassification = CanaryAnalysisResult.builder()
      .name("test-metric-nodata")
      .classification(Nodata.toString)
      .groups(List[String]("test-group").asJava)
      .build()

    val scores = weightedSumScorer.score(List(metricClassification))
    assert(scores.summaryScore == 0.0)
  }

}
