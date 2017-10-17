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

import java.util

import com.netflix.kayenta.canary.results._
import com.netflix.kayenta.canary.{CanaryClassifierThresholdsConfig, CanaryConfig, CanaryJudge}
import com.netflix.kayenta.judge.classifiers.metric.MannWhitneyClassifier
import com.netflix.kayenta.judge.classifiers.score.{ScoreClassification, ThresholdScoreClassifier}
import com.netflix.kayenta.judge.detectors.IQRDetector
import com.netflix.kayenta.judge.scorers.{ScoreResult, WeightedSumScorer}
import com.netflix.kayenta.judge.stats.DescriptiveStatistics
import com.netflix.kayenta.metrics.MetricSetPair
import com.netflix.kayenta.r.MannWhitney
import com.typesafe.scalalogging.StrictLogging
import org.springframework.stereotype.Component

import scala.collection.JavaConverters._

case class Metric(name: String, values: Array[Double], label: String)

@Component
class NetflixACAJudge extends CanaryJudge with StrictLogging {
  private final val judgeName = "NetflixACAJudge-v1.0"

  override def isVisible: Boolean = true

  override def getName: String = judgeName

  override def judge(canaryConfig: CanaryConfig,
                     scoreThresholds: CanaryClassifierThresholdsConfig,
                     metricSetPairList: util.List[MetricSetPair]): CanaryJudgeResult = {

    //Connect to RServe to perform the Mann-Whitney U Test
    val mw = new MannWhitney()

    //Metric Classification
    val metricResults = metricSetPairList.asScala.toList.map { metricPair =>
      classifyMetric(canaryConfig, metricPair, mw)
    }

    //Disconnect from RServe
    mw.disconnect()

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
    * @param scores
    * @param scoreClassification
    * @param metricResults
    * @return
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
    val results = metricResults.asJava;
    CanaryJudgeResult.builder()
      .judgeName(judgeName)
      .score(summaryScore)
      .results(results)
      .groupScores(groupScores.asJava)
      .build()
  }

  /**
    * Metric Transformations
    * @param metric
    * @return
    */
  def transformMetric(metric: Metric): Metric = {
    val detector = new IQRDetector(factor = 3.0, reduceSensitivity = true)
    val transform = Function.chain[Metric](Seq(
      Transforms.removeNaNs(_),
      Transforms.removeOutliers(_, detector)))
    transform(metric)
  }

  /**
    * Metric Validation
    * @param metric
    * @return
    */
  def validateMetric(metric: Metric): ValidationResult = {
    val validators: List[Metric => ValidationResult] = List(
      Validators.checkEmptyArray(_),
      Validators.checkNaNArray(_))

    val validationResults = validators.map(fn => fn(metric))
    val invalidResults = validationResults.filter(_.valid == false)
    val validResults = validationResults.filter(_.valid == true)

    if(invalidResults.nonEmpty) invalidResults.head else validResults.head
  }

  /**
    * Metric Classification
    * @param canaryConfig
    * @param metric
    * @return
    */
  def classifyMetric(canaryConfig: CanaryConfig, metric: MetricSetPair, mw: MannWhitney): CanaryAnalysisResult ={

    val metricConfig = canaryConfig.getMetrics.asScala.find(m => m.getName == metric.getName) match {
      case Some(config) => config
      case None => throw new IllegalArgumentException(s"Could not find metric config for ${metric.getName}")
    }

    val experimentValues = metric.getValues.get("experiment").asScala.map(_.toDouble).toArray
    val controlValues = metric.getValues.get("control").asScala.map(_.toDouble).toArray

    val experiment = Metric(metric.getName, experimentValues, label="Canary")
    val control = Metric(metric.getName, controlValues, label="Baseline")

    logger.debug("Metric " + metric.getName + " Experiment data point count: " + experimentValues.length)
    logger.debug("Metric " + metric.getName + " Control data point count: " + controlValues.length)

    //=============================================
    // Metric Validation
    // ============================================
    //todo (csanden) Implement metric validation
    val validExperimentMetric = validateMetric(experiment)
    val validControlMetric = validateMetric(control)

    //=============================================
    // Metric Transformation
    // ============================================
    //Transform the metrics (remove NaN values, remove outliers, etc)
    val transformedExperiment = transformMetric(experiment)
    val transformedControl = transformMetric(control)

    //=============================================
    // Calculate metric statistics
    // ============================================
    //Calculate summary statistics such as mean, median, max, etc.
    val experimentStats = DescriptiveStatistics.summary(transformedExperiment)
    val controlStats = DescriptiveStatistics.summary(transformedControl)

    //=============================================
    // Metric Classification
    // ============================================
    //Use the Mann-Whitney algorithm to compare the experiment and control populations
    val mannWhitney = new MannWhitneyClassifier(fraction = 0.25, confLevel = 0.98, mw)
    val metricClassification = mannWhitney.classify(transformedControl, transformedExperiment)

    CanaryAnalysisResult.builder()
      .name(metric.getName)
      .id(metric.getId)
      .tags(metric.getTags)
      .classification(metricClassification.classification.toString)
      .classificationReason(metricClassification.reason.orNull)
      .groups(metricConfig.getGroups)
      .experimentMetadata(Map("stats" -> DescriptiveStatistics.toMap(experimentStats).asJava.asInstanceOf[Object]).asJava)
      .controlMetadata(Map("stats" -> DescriptiveStatistics.toMap(controlStats).asJava.asInstanceOf[Object]).asJava)
      .resultMetadata(Map("ratio" -> metricClassification.ratio.asInstanceOf[Object]).asJava)
      .build()

  }

}
