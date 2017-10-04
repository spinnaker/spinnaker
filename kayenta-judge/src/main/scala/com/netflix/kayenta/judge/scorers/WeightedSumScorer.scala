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

import scala.collection.JavaConverters._

class WeightedSumScorer(groupWeights: Map[String, Double]) extends BaseScorer{
  //Todo (csanden) Use the Classification Label not the String value

  /**
    *
    * @param groupName
    * @param classificationLabels
    * @return
    */
  private def calculateGroupScore(groupName: String, classificationLabels: List[String]): GroupScore ={

    val labelCounts = classificationLabels.groupBy(identity).mapValues(_.size)
    val numMetrics = classificationLabels.size

    val numPass = labelCounts.getOrElse("Pass", 0)
    val numHigh = labelCounts.getOrElse("High", 0)
    val numLow = labelCounts.getOrElse("Low", 0)
    val numTotal = numHigh + numLow + numPass

    val hasNoData = if(numTotal == 0) true else false
    val score = if(!hasNoData) (numPass/numTotal.toDouble) * 100 else 0.0

    GroupScore(groupName, score, hasNoData, labelCounts, numMetrics)
  }

  /**
    *
    * @param metricResults
    * @return
    */
  private def calculateGroupScores(metricResults: List[CanaryAnalysisResult]): List[GroupScore] ={

    val groupLabels = metricResults.flatMap{ metric =>
      metric.getGroups.asScala.map{ group => (group, metric.getClassification)}
    }.groupBy(_._1).mapValues(_.map(_._2))

    groupLabels.map{ case (groupName, labels) => calculateGroupScore(groupName, labels)}.toList
  }


  private def calculateSummaryScore(groupResults: List[GroupScore]): Double ={
    //Todo (csanden) throw exception if the group weights exceed 100%

    val groupWeightSum = groupWeights.values.sum
    val groupWeightSet = groupWeights.keySet

    //Get the set of all groups from the results
    val groupSet = groupResults.map(group => group.name).toSet

    //Determine which groups do not have weights associated with them
    val groupDifference = groupSet.diff(groupWeightSet)
    val calculatedWeight = if(groupDifference.nonEmpty) (100-groupWeightSum)/groupDifference.size else 0.0

    //Compute the summary score based on the group score and weights
    var summaryScore: Double = 100
    groupResults.filter(!_.noData).foreach{ group =>
      val weight:Double = groupWeights.getOrElse(group.name, calculatedWeight)
      summaryScore -= (100-group.score)*weight/100
    }

    summaryScore
  }

  /**
    *
    * @param results
    * @return
    */
  override def score(results: List[CanaryAnalysisResult]): ScoreResult = {
    val groupScores = calculateGroupScores(results)
    val summaryScore = calculateSummaryScore(groupScores)
    ScoreResult(Some(groupScores), summaryScore, results.size)
  }

}


