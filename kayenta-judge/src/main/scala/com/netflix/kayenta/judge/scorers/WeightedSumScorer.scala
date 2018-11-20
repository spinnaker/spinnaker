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

package com.netflix.kayenta.judge.scorers

import com.netflix.kayenta.canary.results.CanaryAnalysisResult
import com.netflix.kayenta.judge.classifiers.metric.{High, Low, Nodata, Pass, NodataFailMetric}
import org.apache.commons.math.util.MathUtils

import scala.collection.JavaConverters._

class WeightedSumScorer(groupWeights: Map[String, Double]) extends BaseScorer {

  val NODATA_THRESHOLD = 50

  private def calculateGroupScore(groupName: String, classificationLabels: List[String]): GroupScore = {
    val labelCounts = classificationLabels.groupBy(identity).mapValues(_.size)
    val numMetrics = classificationLabels.size

    val numPass = labelCounts.getOrElse(Pass.toString, 0)
    val numHigh = labelCounts.getOrElse(High.toString, 0)
    val numLow = labelCounts.getOrElse(Low.toString, 0)
    val numMissingRequiredData = labelCounts.getOrElse(NodataFailMetric.toString, 0)
    val numTotal = numHigh + numLow + numPass + numMissingRequiredData

    val hasNoData = numTotal == 0
    val score = if (hasNoData) 0.0 else (numPass/numTotal.toDouble) * 100

    GroupScore(groupName, score, hasNoData, labelCounts, numMetrics)
  }

  private def calculateGroupScores(metricResults: List[CanaryAnalysisResult]): List[GroupScore] = {

    val groupLabels = metricResults.flatMap { metric =>
      metric.getGroups.asScala.map { group => (group, metric.getClassification) }
    }.groupBy(_._1).mapValues(_.map(_._2))

    groupLabels.map { case (groupName, labels) => calculateGroupScore(groupName, labels) }.toList
  }

  private def calculateSummaryScore(groupResults: List[GroupScore]): Double ={

    val groupWeightSum = groupWeights.values.sum
    val groupWeightSet = groupWeights.keySet

    //Get the set of all groups from the results
    val groupSet = groupResults.map(group => group.name).toSet

    //Determine which groups do not have weights associated with them
    val groupDifference = groupSet.diff(groupWeightSet)
    val calculatedWeight = if (groupDifference.nonEmpty)
      (100 - groupWeightSum) / groupDifference.size
    else 0.0

    //Compute the summary score based on the group score and weights
    var summaryScore: Double = 0.0
    groupResults.foreach { group =>
      val effectiveScore = if (group.noData) 100.0 else group.score
      val weight: Double = groupWeights.getOrElse(group.name, calculatedWeight)
      summaryScore += effectiveScore * (weight / 100)
    }

    //Round the summary score to two decimal place
    MathUtils.round(summaryScore, 2)
  }

  def criticalFailures(results: List[CanaryAnalysisResult]): List[CanaryAnalysisResult] = {
    results.filter { result => result.isCritical && !result.getClassification.equals(Pass.toString) }
  }

  def tooManyNodata(results: List[CanaryAnalysisResult]): Boolean = {
    val nodata = results.filter { result => result.getClassification.equals(Nodata.toString) }
    val nodataPercent = nodata.size.toDouble / results.size * 100
    nodataPercent >= NODATA_THRESHOLD
  }

  override def score(results: List[CanaryAnalysisResult]): ScoreResult = {
    val groupScores = calculateGroupScores(results)

    val failures = criticalFailures(results)
    if (failures.nonEmpty) {
      val reason = s"Canary Failed: ${failures.head.getClassificationReason}"
      ScoreResult(Some(groupScores), 0.0, results.size, Some(reason))

    } else if (tooManyNodata(results)) {
      val reason = s"Canary Failed: $NODATA_THRESHOLD% or more metrics returned ${Nodata.toString.toUpperCase}"
      ScoreResult(Some(groupScores), 0.0, results.size, Some(reason))

    } else {
      val summaryScore = calculateSummaryScore(groupScores)
      ScoreResult(Some(groupScores), summaryScore, results.size , reason = None)
    }
  }
}


