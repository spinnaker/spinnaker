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

package com.netflix.kayenta.judge.classifiers.metric

import com.netflix.kayenta.judge.Metric

sealed trait MetricClassificationLabel
case object Pass extends MetricClassificationLabel
case object High extends MetricClassificationLabel
case object Low extends MetricClassificationLabel
case object NodataFailMetric extends MetricClassificationLabel
case object Nodata extends MetricClassificationLabel
case object Error extends MetricClassificationLabel

sealed trait MetricDirection
object MetricDirection {
  case object Increase extends MetricDirection
  case object Decrease extends MetricDirection
  case object Either extends MetricDirection

  def parse(directionalityString: String): MetricDirection = {
    directionalityString match {
      case "increase" => MetricDirection.Increase
      case "decrease" => MetricDirection.Decrease
      case "either" => MetricDirection.Either
      case _ =>  MetricDirection.Either
    }
  }
}

sealed trait NaNStrategy
object NaNStrategy {
  case object Remove extends NaNStrategy
  case object Replace extends NaNStrategy

  def parse(nanStrategy: String): NaNStrategy = {
    nanStrategy match {
      case "remove" => NaNStrategy.Remove
      case "replace" => NaNStrategy.Replace
      case _ => NaNStrategy.Remove
    }
  }
}

case class MetricClassification(classification: MetricClassificationLabel,
                                reason: Option[String],
                                deviation: Double,
                                critical: Boolean,
                                isDataRequired: Boolean = false)

abstract class BaseMetricClassifier {
  def classify(control: Metric, experiment: Metric,
               direction: MetricDirection = MetricDirection.Either,
               nanStrategy: NaNStrategy = NaNStrategy.Remove,
               isCriticalMetric: Boolean = false,
               isDataRequired: Boolean = false): MetricClassification
}
