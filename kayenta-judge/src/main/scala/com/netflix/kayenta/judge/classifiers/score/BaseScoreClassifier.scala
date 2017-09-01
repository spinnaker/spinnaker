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

package com.netflix.kayenta.judge.classifiers.score

import com.netflix.kayenta.judge.scorers.ScoreResult

sealed trait ScoreClassificationLabel
case object Pass extends ScoreClassificationLabel
case object Fail extends ScoreClassificationLabel
case object Marginal extends ScoreClassificationLabel
case object Nodata extends ScoreClassificationLabel
case object Error extends ScoreClassificationLabel

case class ScoreClassification(classification: ScoreClassificationLabel, reason: Option[String], score: Double)

abstract class BaseScoreClassifier {
  def classify(scoreResults: ScoreResult): ScoreClassification
}
