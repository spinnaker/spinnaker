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

import com.netflix.kayenta.canary.results.{CanaryAnalysisResult, CanaryJudgeResult}
import com.netflix.kayenta.canary.{CanaryClassifierThresholdsConfig, CanaryConfig, CanaryJudge}
import com.netflix.kayenta.judge.classifiers.metric.{High, Low, Pass}
import com.netflix.kayenta.judge.scorers.ScoringHelper
import com.netflix.kayenta.judge.stats.DescriptiveStatistics
import com.netflix.kayenta.metrics.MetricSetPair
import org.springframework.stereotype.Component

import scala.collection.JavaConverters._

@Component
class RandomDummyJudge extends CanaryJudge {
  private final val judgeName = "dredd-v1.0"

  override def getName: String = judgeName

  val random = new scala.util.Random()

  override def judge(canaryConfig: CanaryConfig,
                     scoreThresholds: CanaryClassifierThresholdsConfig,
                     metricSetPairList: util.List[MetricSetPair]): CanaryJudgeResult = {

      val metricResults = metricSetPairList.asScala.toList.map { metricPair =>
        val metricConfig = canaryConfig.getMetrics.asScala.find(m => m.getName == metricPair.getName) match {
        case Some(config) => config
        case None => throw new IllegalArgumentException(s"Could not find metric config for ${metricPair.getName}")
      }

      val experimentValues = metricPair.getValues.get("experiment").asScala.map(_.toDouble).toArray
      val controlValues = metricPair.getValues.get("control").asScala.map(_.toDouble).toArray

      val experimentMetric = Metric(metricPair.getName, experimentValues, label="Canary")
      val controlMetric = Metric(metricPair.getName, controlValues, label="Baseline")

      val experimentStats = DescriptiveStatistics.summary(experimentMetric)
      val controlStats = DescriptiveStatistics.summary(controlMetric)

      val randomValue = "%.2f".format(random.nextDouble * 100).toDouble

      // The classification of each individual metric will be done randomly, ignoring the actual data points.
      val classification = {
        if (randomValue >= 66) {
          High.toString
        } else if (randomValue >= 33) {
          Pass.toString
        } else {
          Low.toString
        }
      }

      CanaryAnalysisResult.builder()
        .name(metricPair.getName)
        .id(metricPair.getId)
        .tags(metricPair.getTags)
        .classification(classification)
        .groups(metricConfig.getGroups)
        .experimentMetadata(Map("stats" -> experimentStats.toMap.asJava.asInstanceOf[Object]).asJava)
        .controlMetadata(Map("stats" -> controlStats.toMap.asJava.asInstanceOf[Object]).asJava)
        .build()
    }

    val scoringHelper = new ScoringHelper(judgeName)
    scoringHelper.score(canaryConfig, scoreThresholds, metricResults)
  }

}
