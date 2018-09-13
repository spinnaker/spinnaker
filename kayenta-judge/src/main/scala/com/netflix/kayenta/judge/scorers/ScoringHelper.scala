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

import java.util

import com.netflix.kayenta.canary.{CanaryClassifierThresholdsConfig, CanaryConfig}
import com.netflix.kayenta.canary.results.{CanaryAnalysisResult, CanaryJudgeGroupScore, CanaryJudgeResult, CanaryJudgeScore}
import com.netflix.kayenta.judge.classifiers.score.{ScoreClassification, ThresholdScoreClassifier}

import scala.collection.JavaConverters._

class ScoringHelper(judgeName: String) {

  def score(canaryConfig: CanaryConfig,
            scoreThresholds: CanaryClassifierThresholdsConfig,
            metricResults: util.List[CanaryAnalysisResult]): CanaryJudgeResult = {
    score(canaryConfig, scoreThresholds, metricResults.asScala.toList)
  }

  def score(canaryConfig: CanaryConfig,
            scoreThresholds: CanaryClassifierThresholdsConfig,
            metricResults: List[CanaryAnalysisResult]): CanaryJudgeResult = {
    //Get the group weights from the canary configuration
    val groupWeights = Option(canaryConfig.getClassifier.getGroupWeights) match {
      case Some(groups) => groups.asScala.mapValues(_.toDouble).toMap
      case None => Map[String, Double]()
    }

    //Calculate the summary and group scores based on the metric results
    val weightedSumScorer = new WeightedSumScorer(groupWeights)
    val scores = weightedSumScorer.score(metricResults)

    //Classify the summary score
    val scoreClassifier = new ThresholdScoreClassifier(scoreThresholds.getPass, scoreThresholds.getMarginal)
    val scoreClassification = scoreClassifier.classify(scores)

    //Construct the canary result object
    buildCanaryResult(scores, scoreClassification, metricResults)
  }

  /**
    * Build the canary result object
    */
  def buildCanaryResult(scores: ScoreResult, scoreClassification: ScoreClassification,
                        metricResults: List[CanaryAnalysisResult]): CanaryJudgeResult ={

    //Construct the group score result object
    val groupScores = scores.groupScores match {
      case None => List(CanaryJudgeGroupScore.builder().build())
      case Some(groups) => groups.map{ group =>
        CanaryJudgeGroupScore.builder()
          .name(group.name)
          .score(group.score)
          .classification("")
          .classificationReason("")
          .build()
      }
    }

    //Construct the summary score result object
    val summaryScore = CanaryJudgeScore.builder()
      .score(scoreClassification.score)
      .classification(scoreClassification.classification.toString)
      .classificationReason(scoreClassification.reason.getOrElse(""))
      .build()

    //Construct the judge result object
    val results = metricResults.asJava
    CanaryJudgeResult.builder()
      .judgeName(judgeName)
      .score(summaryScore)
      .results(results)
      .groupScores(groupScores.asJava)
      .build()
  }
}
